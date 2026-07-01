package team.shade.lmdbviewer.transfer

import java.io.Reader

/**
 * Parses [TransferRecord]s written by [EntryExporter] back from JSON or NDJSON. CSV is not
 * importable (it is a lossy human rendering), so only [TransferFormat.importable] formats are
 * accepted.
 *
 * NDJSON is read lazily line by line, so large dumps import in bounded memory; JSON is a single array
 * and is read whole. Records with a `db` field carry their target DBI (environment dumps); without
 * one, [TransferRecord.db] is null and the caller decides the destination.
 */
object EntryImporter {

    /** Streams records from [reader] in [format]. The sequence must be consumed before [reader] closes. */
    fun read(reader: Reader, format: TransferFormat): Sequence<TransferRecord> = when (format) {
        TransferFormat.NDJSON ->
            reader.buffered().lineSequence()
                .filter { it.isNotBlank() }
                .map { recordFrom(Json.parse(it)) }
        TransferFormat.JSON -> {
            val root = Json.parse(reader.readText())
            val list = root as? List<*> ?: throw IllegalArgumentException("Expected a JSON array of records")
            list.asSequence().map { recordFrom(it) }
        }
        TransferFormat.CSV ->
            throw IllegalArgumentException("CSV is export-only; import from JSON or NDJSON instead")
    }

    /** Convenience overload for in-memory text (used by tests). */
    fun read(text: String, format: TransferFormat): Sequence<TransferRecord> = read(text.reader(), format)

    private fun recordFrom(node: Any?): TransferRecord {
        val obj = node as? Map<*, *> ?: throw IllegalArgumentException("Expected a record object")
        val db = when (val d = obj["db"]) {
            null -> null
            is String -> d
            else -> throw IllegalArgumentException("'db' must be a string")
        }
        return TransferRecord(db = db, key = field(obj["key"], "key"), value = field(obj["value"], "value"))
    }

    private fun field(node: Any?, name: String): ByteArray {
        val obj = node as? Map<*, *> ?: throw IllegalArgumentException("Missing or malformed '$name' field")
        val enc = obj["enc"] as? String ?: throw IllegalArgumentException("'$name.enc' must be a string")
        val v = obj["v"] as? String ?: throw IllegalArgumentException("'$name.v' must be a string")
        return ByteText.decode(enc, v)
    }
}
