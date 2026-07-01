package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import team.shade.lmdbviewer.transfer.EntryExporter
import team.shade.lmdbviewer.transfer.EntryImporter
import team.shade.lmdbviewer.transfer.TransferFormat
import team.shade.lmdbviewer.transfer.TransferRecord
import java.io.File

/**
 * Access-layer support for export/import: [LmdbConnection.forEachEntry] streaming, batch writes via
 * [MutationOps.putBatch], and an end-to-end export→import round-trip through the transfer layer.
 */
class LmdbTransferTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = TestEnvs.newTempDir()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun forEachEntryStreamsEveryEntryInKeyOrder() {
        TestEnvs.populateDir(dir, "main", mapOf("c" to "3", "a" to "1", "b" to "2"))
        TestEnvs.openReadOnly(dir).use { conn ->
            val seen = mutableListOf<Pair<String, String>>()
            conn.forEachEntry("main") { seen += String(it.key) to String(it.value) }
            assertEquals(listOf("a" to "1", "b" to "2", "c" to "3"), seen)
        }
    }

    @Test
    fun putBatchWritesEveryEntry() {
        TestEnvs.populateDir(dir, "main", emptyMap()) // ensure the env/main DBI exists
        TestEnvs.openWritable(dir).use { conn ->
            conn.mutations.putBatch(
                "main",
                listOf(LmdbEntry("x".b(), "1".b()), LmdbEntry("y".b(), "2".b()), LmdbEntry("z".b(), "3".b())),
            )
            assertEquals(listOf("x", "y", "z"), conn.readPage("main").entries.map { String(it.key) })
        }
    }

    @Test
    fun readOnlyConnectionRejectsPutBatch() {
        TestEnvs.populateDir(dir, "main", mapOf("a" to "1"))
        TestEnvs.openReadOnly(dir).use { conn ->
            try {
                conn.mutations.putBatch("main", listOf(LmdbEntry("b".b(), "2".b())))
                fail("read-only connection must reject putBatch")
            } catch (_: UnsupportedOperationException) {
            }
        }
    }

    @Test
    fun exportThenImportReproducesEntriesLosslessly() {
        // Source data spans text, binary, empty value, and a NUL-bearing key.
        val source = linkedMapOf(
            "alpha".b() to "one".b(),
            byteArrayOf(0x00, 0x01, 0x02) to byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            "empty".b() to ByteArray(0),
            byteArrayOf(0x00) to "nul".b(),
        )
        TestEnvs.populateDirBytes(dir, "src", source)
        TestEnvs.createEmptyDbi(dir, "dst")

        // Export "src" to an NDJSON document via the streaming reader + exporter.
        val doc = StringBuilder()
        TestEnvs.openReadOnly(dir).use { conn ->
            EntryExporter(doc, TransferFormat.NDJSON).use { exp ->
                conn.forEachEntry("src") { exp.write(TransferRecord(null, it.key, it.value)) }
            }
        }

        // Import the document into the empty "dst" DBI in batches.
        TestEnvs.openWritable(dir).use { conn ->
            EntryImporter.read(doc.toString(), TransferFormat.NDJSON).chunked(100).forEach { batch ->
                conn.mutations.putBatch("dst", batch.map { LmdbEntry(it.key, it.value) })
            }
        }

        // "dst" must now hold exactly what "src" holds, byte for byte.
        TestEnvs.openReadOnly(dir).use { conn ->
            assertEquals(collect(conn, "src"), collect(conn, "dst"))
        }
    }

    private fun collect(conn: LmdbConnection, dbi: String): List<Pair<List<Byte>, List<Byte>>> {
        val out = mutableListOf<Pair<List<Byte>, List<Byte>>>()
        conn.forEachEntry(dbi) { out += it.key.toList() to it.value.toList() }
        return out
    }
}
