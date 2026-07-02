package team.shade.lmdbviewer.lmdb

import org.lmdbjava.Dbi
import org.lmdbjava.Env
import org.lmdbjava.KeyRange
import java.nio.charset.StandardCharsets

/**
 * A handle on one open LMDB environment. The single place that talks to lmdbjava for reads.
 * Thread-safe for concurrent page reads: each call opens and closes its own short read txn.
 *
 * With the byte[] buffer proxy, lmdbjava copies keys/values out of the mmap, so the [LmdbEntry]
 * arrays returned here remain valid after their transaction closes.
 *
 * Reads work the same whether the environment was opened read-only or for writing. Writes go
 * through [mutations]: a read-only environment rejects them, a writable one (edit mode) applies
 * them. See [MutationOps].
 */
class LmdbConnection internal constructor(
    val path: String,
    private val env: Env<ByteArray>,
    /** True when the environment was opened without `MDB_RDONLY_ENV`, i.e. edit mode. */
    val writable: Boolean = false,
) : AutoCloseable {

    /** Invoked (off the EDT) when a write grows the map size, with the new size in bytes. */
    var onMapResized: (Long) -> Unit = {}

    /** The write seam for this connection: rejects everything unless [writable]. */
    val mutations: MutationOps =
        if (writable) WritableMutationOps(env) { size -> onMapResized(size) } else ReadOnlyMutationOps

    /** Lists the main (unnamed) DBI plus every named DBI, with entry counts. */
    fun listDatabases(): List<DbiInfo> = guarded {
        // A DBI handle must be opened *before* the transaction that reads its stats begins, so open
        // every handle first (each open uses its own short txn), then stat them in one read txn.
        val names = buildList {
            add(null) // unnamed/main database, always present
            env.dbiNames.forEach { add(String(it, StandardCharsets.UTF_8)) }
        }
        val handles = names.map { name -> name to runCatching { openDbi(name) }.getOrNull() }

        env.txnRead().use { txn ->
            handles.map { (name, dbi) ->
                val stat = dbi?.let { runCatching { it.stat(txn) }.getOrNull() }
                val flags = dbi?.let {
                    runCatching { it.listFlags(txn).map { f -> f.name.removePrefix("MDB_") }.toSet() }.getOrNull()
                } ?: emptySet()
                DbiInfo(
                    name = name,
                    entryCount = stat?.entries ?: -1L,
                    flags = flags,
                    depth = stat?.depth ?: 0,
                    branchPages = stat?.branchPages ?: 0,
                    leafPages = stat?.leafPages ?: 0,
                    overflowPages = stat?.overflowPages ?: 0,
                )
            }
        }
    }

    /**
     * Reads one page of entries.
     *
     * @param dbiName DBI to read, or null for the main database
     * @param afterKey continuation token from a previous [EntryPage.nextKey]; null starts at the beginning
     *   (or at [prefix] when given)
     * @param prefix when non-null, only entries whose key starts with this prefix are returned
     * @param limit maximum entries in the page
     *
     * Note: continuation uses key ordering. For DUPSORT DBIs, paging boundaries fall on key bounds.
     */
    fun readPage(
        dbiName: String?,
        afterKey: ByteArray? = null,
        prefix: ByteArray? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): EntryPage = guarded {
        val dbi = openDbi(dbiName)
        val range = when {
            afterKey != null -> KeyRange.greaterThan(afterKey)
            prefix != null -> KeyRange.atLeast(prefix)
            else -> KeyRange.all()
        }

        val entries = ArrayList<LmdbEntry>(limit)
        var nextKey: ByteArray? = null

        env.txnRead().use { txn ->
            dbi.iterate(txn, range).use { cursor ->
                val it = cursor.iterator()
                while (it.hasNext()) {
                    val kv = it.next()
                    val key = kv.key()
                    if (prefix != null && !ByteSearch.startsWith(key, prefix)) break

                    if (entries.size == limit) {
                        // One extra record exists -> there is another page; remember where to resume.
                        nextKey = entries.last().key
                        break
                    }
                    entries += LmdbEntry(key, kv.`val`())
                }
            }
        }
        EntryPage(entries, nextKey)
    }

    /**
     * Reads one page of entries that match [query], scanning in key order from [afterKey].
     *
     * Unlike [readPage]'s prefix seek, content search (key/value substring) cannot use a key range,
     * so this scans entries and keeps the ones [SearchQuery.matches] accepts, up to [limit] matches.
     * The continuation token ([EntryPage.nextKey]) is the last **matched** key: the next call resumes
     * with `KeyRange.greaterThan` strictly after it, so no match is shown twice. If the scan reaches
     * the end before filling a page, `nextKey` is null.
     *
     * Notes:
     * - For a [SearchScope.KEY_PREFIX] query prefer [readPage] (a seek); this scans from the start.
     * - As with [readPage], continuation is by key. For DUPSORT DBIs, a page boundary that lands
     *   between duplicates of one key resumes after that whole key, so remaining duplicates of the
     *   boundary key are not re-examined.
     */
    fun scanPage(
        dbiName: String?,
        query: SearchQuery,
        afterKey: ByteArray? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): EntryPage = guarded {
        val dbi = openDbi(dbiName)
        val range = if (afterKey != null) KeyRange.greaterThan(afterKey) else KeyRange.all()

        val entries = ArrayList<LmdbEntry>(limit)
        var nextKey: ByteArray? = null

        env.txnRead().use { txn ->
            dbi.iterate(txn, range).use { cursor ->
                val it = cursor.iterator()
                while (it.hasNext()) {
                    val kv = it.next()
                    val entry = LmdbEntry(kv.key(), kv.`val`())
                    if (!query.matches(entry)) continue
                    entries += entry
                    if (entries.size == limit) {
                        // Full page of matches; there may be more, so resume after this last match.
                        nextKey = entry.key
                        break
                    }
                }
            }
        }
        EntryPage(entries, nextKey)
    }

    /**
     * Streams every entry of [dbiName] (null = main DBI) to [block], in key order, inside one short
     * read txn. Unlike [readPage] there is no limit and nothing is materialised into a list — the
     * caller consumes each [LmdbEntry] as it is produced, so very large DBIs export in bounded memory.
     *
     * lmdbjava copies the bytes out of the mmap, so each [LmdbEntry] stays valid after the txn closes.
     */
    fun forEachEntry(dbiName: String?, block: (LmdbEntry) -> Unit) = guarded {
        val dbi = openDbi(dbiName)
        env.txnRead().use { txn ->
            dbi.iterate(txn, KeyRange.all()).use { cursor ->
                val it = cursor.iterator()
                while (it.hasNext()) {
                    val kv = it.next()
                    block(LmdbEntry(kv.key(), kv.`val`()))
                }
            }
        }
    }

    fun stats(): EnvStats = guarded {
        val info = env.info()
        val stat = env.stat()
        EnvStats(
            path = path,
            mapSize = info.mapSize,
            pageSize = stat.pageSize,
            maxReaders = info.maxReaders,
            numReaders = info.numReaders,
            lastPageNumber = info.lastPageNumber,
            lastTransactionId = info.lastTransactionId,
            dbiCount = env.dbiNames.size + 1,
        )
    }

    /**
     * Clears reader-lock slots left behind by processes/threads that died without closing their read
     * txn, returning how many stale entries were released. Safe to call at any time.
     */
    fun checkStaleReaders(): Int = guarded { env.readerCheck() }

    private fun openDbi(name: String?): Dbi<ByteArray> =
        if (name == null) env.openDbi(null as String?) else env.openDbi(name)

    private fun <T> guarded(block: () -> T): T = ClassLoaderGuard.runWithPluginClassLoader(block)

    override fun close() = guarded { env.close() }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}
