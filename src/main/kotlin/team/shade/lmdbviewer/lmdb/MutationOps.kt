package team.shade.lmdbviewer.lmdb

import org.lmdbjava.Env

/**
 * Write operations against a DBI — the single seam through which the viewer mutates data. An
 * environment opened read-only exposes [ReadOnlyMutationOps] (every call rejected); an environment
 * opened for writing (edit mode) exposes [WritableMutationOps]. All write logic lives here; do not
 * scatter it elsewhere. See the roadmap in CLAUDE.md.
 */
interface MutationOps {
    fun put(dbiName: String?, key: ByteArray, value: ByteArray)
    fun delete(dbiName: String?, key: ByteArray, value: ByteArray?)
}

/** Used when the environment is open read-only: mutations are not permitted. */
object ReadOnlyMutationOps : MutationOps {
    override fun put(dbiName: String?, key: ByteArray, value: ByteArray): Nothing =
        throw UnsupportedOperationException("This LMDB environment is open read-only; enable edit mode to modify it")

    override fun delete(dbiName: String?, key: ByteArray, value: ByteArray?): Nothing =
        throw UnsupportedOperationException("This LMDB environment is open read-only; enable edit mode to modify it")
}

/**
 * Mutations against an environment opened for writing (without `MDB_RDONLY_ENV`). Each operation
 * runs in its own short write transaction and commits before returning.
 *
 * Mirrors the access-layer invariants used for reads: open the DBI handle *before* the transaction
 * that uses it, and run every lmdbjava call under the plugin classloader (see [ClassLoaderGuard]).
 */
internal class WritableMutationOps(private val env: Env<ByteArray>) : MutationOps {

    override fun put(dbiName: String?, key: ByteArray, value: ByteArray) = guarded {
        val dbi = openDbi(dbiName)
        env.txnWrite().use { txn ->
            dbi.put(txn, key, value)
            txn.commit()
        }
    }

    override fun delete(dbiName: String?, key: ByteArray, value: ByteArray?) = guarded {
        val dbi = openDbi(dbiName)
        env.txnWrite().use { txn ->
            // On a non-DUPSORT DBI the data argument is ignored by LMDB; on a DUPSORT DBI passing a
            // value deletes that specific key/value pair, while null removes every duplicate.
            if (value == null) dbi.delete(txn, key) else dbi.delete(txn, key, value)
            txn.commit()
        }
    }

    private fun openDbi(name: String?) =
        if (name == null) env.openDbi(null as String?) else env.openDbi(name)

    private fun <T> guarded(block: () -> T): T = ClassLoaderGuard.runWithPluginClassLoader(block)
}
