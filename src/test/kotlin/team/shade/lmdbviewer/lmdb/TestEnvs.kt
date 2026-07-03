package team.shade.lmdbviewer.lmdb

import org.lmdbjava.ByteArrayProxy
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import org.lmdbjava.EnvFlags
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Shared fixtures for the LMDB access-layer tests. Builds throwaway on-disk environments with
 * lmdbjava and reopens them read-only — the same pattern the original [LmdbConnectionTest] uses,
 * factored out so the edge-case suites don't repeat it.
 *
 * Every helper that opens an [Env] closes it before returning (or hands back an [AutoCloseable]
 * the caller must close). On Windows the mmap'd files stay locked until the env is closed, so any
 * temp directory must only be deleted after all connections to it are closed.
 */
internal object TestEnvs {

    private const val MAP_SIZE = 8L shl 20 // 8 MiB — plenty for fixtures
    private const val MAX_DBS = 10

    /** Creates a fresh empty temp directory to hold a directory-style environment. */
    fun newTempDir(): File = File.createTempFile("lmdb-test", "").let {
        it.delete(); it.mkdirs(); it
    }

    /** Creates a fresh temp path (file deleted) for a single-file (`MDB_NOSUBDIR`) environment. */
    fun newTempFile(): File = File.createTempFile("lmdb-test", ".mdb").also { it.delete() }

    /**
     * Creates [dbiName] in the directory environment at [dir] and writes [entries], then closes the
     * env so it can be reopened read-only.
     */
    fun populateDir(dir: File, dbiName: String, entries: Map<String, String>) =
        populateDirBytes(dir, dbiName, entries.entries.associate { it.key.b() to it.value.b() })

    /** Byte-keyed variant of [populateDir], for binary keys/values. */
    fun populateDirBytes(dir: File, dbiName: String, entries: Map<ByteArray, ByteArray>) {
        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(MAX_DBS).setMapSize(MAP_SIZE).open(dir).use { env ->
            writeAll(env, dbiName, entries)
        }
    }

    /** Creates an empty named DBI in [dir] (no entries) and closes the env. */
    fun createEmptyDbi(dir: File, dbiName: String) {
        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(MAX_DBS).setMapSize(MAP_SIZE).open(dir).use { env ->
            env.openDbi(dbiName, DbiFlags.MDB_CREATE)
        }
    }

    /**
     * Creates a DUPSORT DBI [dbiName] in [dir] where each key maps to several values, then closes the
     * env so it can be reopened. Values for a key are written in the given order (LMDB stores them
     * sorted).
     */
    fun populateDupSort(dir: File, dbiName: String, entries: Map<String, List<String>>) {
        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(MAX_DBS).setMapSize(MAP_SIZE).open(dir).use { env ->
            val dbi = env.openDbi(dbiName, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT)
            env.txnWrite().use { txn ->
                entries.forEach { (k, values) -> values.forEach { v -> dbi.put(txn, k.b(), v.b()) } }
                txn.commit()
            }
        }
    }

    /** Single-file environment variant: opens [file] with `MDB_NOSUBDIR`, writes [entries], closes. */
    fun populateSingleFile(file: File, dbiName: String, entries: Map<String, String>) {
        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(MAX_DBS).setMapSize(MAP_SIZE)
            .open(file, EnvFlags.MDB_NOSUBDIR).use { env ->
                writeAll(env, dbiName, entries.entries.associate { it.key.b() to it.value.b() })
            }
    }

    /** Opens the directory environment at [dir] read-only and wraps it in an [LmdbConnection]. */
    fun openReadOnly(dir: File): LmdbConnection {
        val env = Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(MAX_DBS).setMapSize(MAP_SIZE)
            .open(dir, EnvFlags.MDB_RDONLY_ENV, EnvFlags.MDB_NOTLS)
        return LmdbConnection(dir.absolutePath, env)
    }

    /** Opens [dir] for writing (no `MDB_RDONLY_ENV`) and wraps it in a writable [LmdbConnection]. */
    fun openWritable(dir: File, mapSize: Long = MAP_SIZE): LmdbConnection {
        val env = Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(MAX_DBS).setMapSize(mapSize)
            .open(dir, EnvFlags.MDB_NOTLS)
        return LmdbConnection(dir.absolutePath, env, writable = true)
    }

    private fun writeAll(env: Env<ByteArray>, dbiName: String, entries: Map<ByteArray, ByteArray>) {
        val dbi = env.openDbi(dbiName, DbiFlags.MDB_CREATE)
        env.txnWrite().use { txn ->
            entries.forEach { (k, v) -> dbi.put(txn, k, v) }
            txn.commit()
        }
    }
}

/** UTF-8 bytes of a string. Top-level so test classes in any package can import it. */
internal fun String.b(): ByteArray = toByteArray(StandardCharsets.UTF_8)
