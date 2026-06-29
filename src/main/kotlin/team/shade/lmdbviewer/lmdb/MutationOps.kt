package team.shade.lmdbviewer.lmdb

/**
 * Write operations against a DBI. **v1 is read-only**: the only implementation is [ReadOnlyMutationOps],
 * which rejects every call. This interface exists so that editing (write transactions) can be added
 * later behind a single seam — see the roadmap in CLAUDE.md. Do not put write logic anywhere else.
 */
interface MutationOps {
    fun put(dbiName: String?, key: ByteArray, value: ByteArray)
    fun delete(dbiName: String?, key: ByteArray, value: ByteArray?)
}

/** The v1 implementation: the environment is open read-only, so mutations are not permitted. */
object ReadOnlyMutationOps : MutationOps {
    override fun put(dbiName: String?, key: ByteArray, value: ByteArray): Nothing =
        throw UnsupportedOperationException("LMDB Viewer is read-only in this version")

    override fun delete(dbiName: String?, key: ByteArray, value: ByteArray?): Nothing =
        throw UnsupportedOperationException("LMDB Viewer is read-only in this version")
}
