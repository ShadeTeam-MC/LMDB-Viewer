package team.shade.lmdbviewer.transfer

/**
 * Streams [TransferRecord]s to an [Appendable] in a chosen [TransferFormat]. Push-based so the caller
 * can feed records straight from a cursor without materialising them: create it, call [write] per
 * record, then [close] (or use it in a `use { }` block) to emit any trailing syntax.
 *
 * @param includeDb whether to carry the per-record DBI name (used for environment-wide dumps that
 *   span multiple databases). For JSON/NDJSON the `db` field is written only when present; for CSV it
 *   controls whether a `db` column exists.
 */
class EntryExporter(
    private val out: Appendable,
    private val format: TransferFormat,
    private val includeDb: Boolean = false,
) : AutoCloseable {

    private var started = false

    private fun start() {
        if (started) return
        started = true
        when (format) {
            TransferFormat.JSON -> out.append("[")
            TransferFormat.NDJSON -> {}
            TransferFormat.CSV -> out.append(csvHeader()).append("\n")
        }
    }

    fun write(record: TransferRecord) {
        start()
        when (format) {
            TransferFormat.JSON -> {
                if (count > 0) out.append(",")
                out.append("\n  ")
                appendJsonRecord(record)
            }
            TransferFormat.NDJSON -> {
                appendJsonRecord(record)
                out.append("\n")
            }
            TransferFormat.CSV -> out.append(csvRow(record)).append("\n")
        }
        count++
    }

    override fun close() {
        start() // ensure a valid empty document even when nothing was written
        if (format == TransferFormat.JSON) {
            if (count > 0) out.append("\n")
            out.append("]\n")
        }
    }

    private var count = 0

    private fun appendJsonRecord(record: TransferRecord) {
        out.append("{")
        var first = true
        if (record.db != null) {
            out.append("\"db\":")
            Json.appendString(out, record.db)
            first = false
        }
        if (!first) out.append(",")
        out.append("\"key\":")
        appendJsonField(record.key)
        out.append(",\"value\":")
        appendJsonField(record.value)
        out.append("}")
    }

    private fun appendJsonField(bytes: ByteArray) {
        val (enc, v) = ByteText.encode(bytes)
        out.append("{\"enc\":\"").append(enc).append("\",\"v\":")
        Json.appendString(out, v)
        out.append("}")
    }

    private fun csvHeader(): String = if (includeDb) "db,key,value" else "key,value"

    private fun csvRow(record: TransferRecord): String {
        val cells = buildList {
            if (includeDb) add(record.db ?: "")
            add(ByteText.human(record.key))
            add(ByteText.human(record.value))
        }
        return cells.joinToString(",") { csvQuote(it) }
    }

    /** RFC 4180 quoting: wrap in quotes and double internal quotes when the cell needs it. */
    private fun csvQuote(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
}
