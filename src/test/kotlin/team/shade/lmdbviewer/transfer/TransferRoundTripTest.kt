package team.shade.lmdbviewer.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Unit tests for the platform-free transfer layer: lossless JSON/NDJSON round-trips for text and
 * binary, encoding-tag selection, CSV rendering/escaping, and the export-only CSV guard.
 */
class TransferRoundTripTest {

    private fun b(s: String): ByteArray = s.toByteArray(StandardCharsets.UTF_8)

    private fun export(records: List<TransferRecord>, format: TransferFormat, includeDb: Boolean = false): String {
        val sb = StringBuilder()
        EntryExporter(sb, format, includeDb).use { exp -> records.forEach { exp.write(it) } }
        return sb.toString()
    }

    /** A representative mix: plain text, binary, empty value, unicode, and a lone NUL byte. */
    private fun sampleRecords(db: String? = null): List<TransferRecord> = listOf(
        TransferRecord(db, b("plain-key"), b("plain value")),
        TransferRecord(db, byteArrayOf(0x00, 0x7F.toByte(), 0xFF.toByte(), 0x10), byteArrayOf(0xDE.toByte(), 0xAD.toByte())),
        TransferRecord(db, b("empty"), ByteArray(0)),
        TransferRecord(db, b("юникод"), b("значение — ✓")),
        TransferRecord(db, byteArrayOf(0x00), b("nul-key")),
    )

    private fun assertRoundTrip(format: TransferFormat, db: String? = null) {
        val records = sampleRecords(db)
        val text = export(records, format)
        val back = EntryImporter.read(text, format).toList()
        assertEquals("record count", records.size, back.size)
        records.zip(back).forEach { (expected, actual) ->
            assertEquals("db", expected.db, actual.db)
            assertArrayEquals("key", expected.key, actual.key)
            assertArrayEquals("value", expected.value, actual.value)
        }
    }

    @Test
    fun ndjsonRoundTripsAllByteShapes() = assertRoundTrip(TransferFormat.NDJSON)

    @Test
    fun jsonRoundTripsAllByteShapes() = assertRoundTrip(TransferFormat.JSON)

    @Test
    fun ndjsonRoundTripsWithDbTag() = assertRoundTrip(TransferFormat.NDJSON, db = "colors")

    @Test
    fun jsonRoundTripsWithDbTag() = assertRoundTrip(TransferFormat.JSON, db = "colors")

    @Test
    fun textUsesUtf8EncodingAndBinaryUsesBase64() {
        val text = export(
            listOf(TransferRecord(null, b("k"), byteArrayOf(0xFF.toByte(), 0x00))),
            TransferFormat.NDJSON,
        )
        assertTrue("text key should be utf8-tagged", text.contains("\"enc\":\"utf8\""))
        assertTrue("binary value should be base64-tagged", text.contains("\"enc\":\"base64\""))
    }

    @Test
    fun emptyJsonExportIsAValidEmptyDocument() {
        val text = export(emptyList(), TransferFormat.JSON)
        assertEquals(emptyList<TransferRecord>(), EntryImporter.read(text, TransferFormat.JSON).toList())
    }

    @Test
    fun emptyNdjsonExportImportsToNothing() {
        val text = export(emptyList(), TransferFormat.NDJSON)
        assertEquals(emptyList<TransferRecord>(), EntryImporter.read(text, TransferFormat.NDJSON).toList())
    }

    @Test
    fun csvQuotesCommasQuotesAndNewlines() {
        val text = export(
            listOf(TransferRecord(null, b("a,b"), b("line1\nline2 \"q\""))),
            TransferFormat.CSV,
        )
        val lines = text.trimEnd().lines()
        assertEquals("key,value", lines.first())
        // The comma-bearing key and the quote/newline value are both wrapped and quotes doubled.
        assertTrue(text.contains("\"a,b\""))
        assertTrue(text.contains("\"\"q\"\""))
    }

    @Test
    fun csvIncludesDbColumnWhenRequested() {
        val text = export(listOf(TransferRecord("main", b("k"), b("v"))), TransferFormat.CSV, includeDb = true)
        assertEquals("db,key,value", text.trimEnd().lines().first())
    }

    @Test
    fun csvIsNotImportable() {
        try {
            EntryImporter.read("key,value\nk,v\n", TransferFormat.CSV).toList()
            fail("CSV import must be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun jsonParserHandlesUnicodeEscapes() {
        // A NUL byte is escaped in JSON and must parse back to a single 0 byte.
        val text = export(listOf(TransferRecord(null, b("k"), byteArrayOf(0))), TransferFormat.JSON)
        assertTrue(text.contains("\\u0000"))
        val back = EntryImporter.read(text, TransferFormat.JSON).single()
        assertArrayEquals(byteArrayOf(0), back.value)
    }

    @Test
    fun formatDetectionFromFileName() {
        assertEquals(TransferFormat.JSON, TransferFormat.fromFileName("dump.json"))
        assertEquals(TransferFormat.NDJSON, TransferFormat.fromFileName("path/to/DUMP.NDJSON"))
        assertEquals(TransferFormat.CSV, TransferFormat.fromFileName("x.csv"))
        assertEquals(null, TransferFormat.fromFileName("noext"))
    }
}
