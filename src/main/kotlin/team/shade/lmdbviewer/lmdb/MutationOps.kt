package team.shade.lmdbviewer.lmdb

import org.lmdbjava.Env
import org.lmdbjava.LmdbException

/**
 * Write operations against a DBI — the single seam through which the viewer mutates data. An
 * environment opened read-only exposes [ReadOnlyMutationOps] (every call rejected); an environment
 * opened for writing (edit mode) exposes [WritableMutationOps]. All write logic lives here; do not
 * scatter it elsewhere. See the roadmap in CLAUDE.md.
 */
interface MutationOps {
    fun put(dbiName: String?, key: ByteArray, value: ByteArray)
    fun delete(dbiName: String?, key: ByteArray, value: ByteArray?)

    /**
     * Replaces the pair (`key`, [oldValue]) with (`key`, [newValue]). On a DUPSORT DBI a plain [put]
     * would *add* a second value instead of editing one, so this deletes the old pair and puts the
     * new one. The default runs the two steps separately; [WritableMutationOps] overrides it to do
     * both in one write txn (atomic). On a non-DUPSORT DBI it is equivalent to `put(key, newValue)`.
     */
    fun replace(dbiName: String?, key: ByteArray, oldValue: ByteArray, newValue: ByteArray) {
        delete(dbiName, key, oldValue)
        put(dbiName, key, newValue)
    }

    /**
     * Writes [entries] into [dbiName] as a single unit (used by import). The default falls back to a
     * [put] per entry; [WritableMutationOps] overrides it to commit the whole batch in one write txn.
     */
    fun putBatch(dbiName: String?, entries: List<LmdbEntry>) {
        entries.forEach { put(dbiName, it.key, it.value) }
    }
}

/** Used when the environment is open read-only: mutations are not permitted. */
object ReadOnlyMutationOps : MutationOps {
    override fun put(dbiName: String?, key: ByteArray, value: ByteArray): Nothing =
        throw UnsupportedOperationException("This LMDB environment is open read-only; enable edit mode to modify it")

    override fun delete(dbiName: String?, key: ByteArray, value: ByteArray?): Nothing =
        throw UnsupportedOperationException("This LMDB environment is open read-only; enable edit mode to modify it")

    override fun replace(dbiName: String?, key: ByteArray, oldValue: ByteArray, newValue: ByteArray): Nothing =
        throw UnsupportedOperationException("This LMDB environment is open read-only; enable edit mode to modify it")

    override fun putBatch(dbiName: String?, entries: List<LmdbEntry>): Nothing =
        throw UnsupportedOperationException("This LMDB environment is open read-only; enable edit mode to modify it")
}

/**
 * Mutations against an environment opened for writing (without `MDB_RDONLY_ENV`). Each operation
 * runs in its own short write transaction and commits before returning.
 *
 * Mirrors the access-layer invariants used for reads: open the DBI handle *before* the transaction
 * that uses it, and run every lmdbjava call under the plugin classloader (see [ClassLoaderGuard]).
 */
internal class WritableMutationOps(
    private val env: Env<ByteArray>,
    private val onMapResized: (Long) -> Unit = {},
) : MutationOps {

    override fun put(dbiName: String?, key: ByteArray, value: ByteArray) = guarded {
        withGrowth {
            val dbi = openDbi(dbiName)
            env.txnWrite().use { txn ->
                dbi.put(txn, key, value)
                txn.commit()
            }
        }
    }

    override fun delete(dbiName: String?, key: ByteArray, value: ByteArray?) = guarded {
        withGrowth {
            val dbi = openDbi(dbiName)
            env.txnWrite().use { txn ->
                // On a non-DUPSORT DBI the data argument is ignored by LMDB; on a DUPSORT DBI passing a
                // value deletes that specific key/value pair, while null removes every duplicate.
                if (value == null) dbi.delete(txn, key) else dbi.delete(txn, key, value)
                txn.commit()
            }
        }
    }

    override fun replace(dbiName: String?, key: ByteArray, oldValue: ByteArray, newValue: ByteArray) = guarded {
        withGrowth {
            val dbi = openDbi(dbiName)
            env.txnWrite().use { txn ->
                // Atomic edit of one pair: drop the old value, add the new one. On DUPSORT this edits a
                // single duplicate; on a normal DBI it is equivalent to overwriting the key.
                dbi.delete(txn, key, oldValue)
                dbi.put(txn, key, newValue)
                txn.commit()
            }
        }
    }

    override fun putBatch(dbiName: String?, entries: List<LmdbEntry>) = guarded {
        if (entries.isEmpty()) return@guarded
        withGrowth {
            val dbi = openDbi(dbiName)
            env.txnWrite().use { txn ->
                entries.forEach { dbi.put(txn, it.key, it.value) }
                txn.commit()
            }
        }
    }

    /** Runs [op]; on `MDB_MAP_FULL` grows the map (doubling, up to [MAX_MAP_SIZE]) and retries. */
    private fun withGrowth(op: () -> Unit) {
        while (true) {
            try {
                op()
                return
            } catch (e: Env.MapFullException) {
                val current = env.info().mapSize
                val next = (current * 2).coerceAtMost(MAX_MAP_SIZE)
                if (next <= current) throw LmdbException("The environment is full (map size limit reached).")
                env.setMapSize(next) // safe only with no open txn — the failed txn already closed
                onMapResized(next)
            }
        }
    }

    private fun openDbi(name: String?) =
        if (name == null) env.openDbi(null as String?) else env.openDbi(name)

    private fun <T> guarded(block: () -> T): T = ClassLoaderGuard.runWithPluginClassLoader(block)

    private companion object {
        const val MAX_MAP_SIZE = 16L shl 30 // 16 GiB ceiling
    }
}
