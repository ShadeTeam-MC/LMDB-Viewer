package team.shade.lmdbviewer.lmdb

/** A single key/value record from a DBI. Key and value are raw bytes. */
data class LmdbEntry(
    val key: ByteArray,
    val value: ByteArray,
) {
    val valueSize: Int get() = value.size
    val keySize: Int get() = key.size

    // Identity by content so table models de-duplicate / compare correctly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LmdbEntry) return false
        return key.contentEquals(other.key) && value.contentEquals(other.value)
    }

    override fun hashCode(): Int = 31 * key.contentHashCode() + value.contentHashCode()
}

/**
 * One page of entries plus a continuation token.
 *
 * @param entries the records in this page (sorted by key)
 * @param nextKey the key to start *after* for the next page, or null if this is the last page
 */
data class EntryPage(
    val entries: List<LmdbEntry>,
    val nextKey: ByteArray?,
) {
    val hasMore: Boolean get() = nextKey != null
}

/** Metadata about a named sub-database (DBI) within an environment. */
data class DbiInfo(
    /** The DBI name, or null for the unnamed/main database. */
    val name: String?,
    val entryCount: Long,
    val flags: Set<String>,
) {
    val displayName: String get() = name ?: "(main)"
    val isDupSort: Boolean get() = "DUPSORT" in flags
}

/** Environment-level statistics shown in the info panel. */
data class EnvStats(
    val path: String,
    val mapSize: Long,
    val pageSize: Int,
    val maxReaders: Int,
    val numReaders: Int,
    val lastPageNumber: Long,
    val lastTransactionId: Long,
    val dbiCount: Int,
)
