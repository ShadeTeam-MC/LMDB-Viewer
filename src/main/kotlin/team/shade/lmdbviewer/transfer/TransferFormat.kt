package team.shade.lmdbviewer.transfer

/**
 * File formats for exporting and importing DBI contents.
 *
 * [JSON] and [NDJSON] are lossless and round-trippable (each byte field is tagged utf8/base64, see
 * [ByteText]); [CSV] is a human-readable, export-only rendering.
 */
enum class TransferFormat(val extension: String, val importable: Boolean) {
    /** A single JSON array of record objects. */
    JSON("json", true),

    /** Newline-delimited JSON — one record object per line; streams without holding the whole file. */
    NDJSON("ndjson", true),

    /** Comma-separated values with a header row; human-readable, not importable. */
    CSV("csv", false);

    companion object {
        /** Matches a file name / extension (with or without leading dot) to a format, or null. */
        fun fromFileName(name: String): TransferFormat? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return values().firstOrNull { it.extension == ext }
        }
    }
}

/**
 * One exported/imported record: a key/value pair, optionally tagged with the DBI it belongs to
 * ([db] is non-null only for environment-wide dumps that span multiple databases).
 */
data class TransferRecord(
    val db: String?,
    val key: ByteArray,
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransferRecord) return false
        return db == other.db && key.contentEquals(other.key) && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = db?.hashCode() ?: 0
        result = 31 * result + key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}
