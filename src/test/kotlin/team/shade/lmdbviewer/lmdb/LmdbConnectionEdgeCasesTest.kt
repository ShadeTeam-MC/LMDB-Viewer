package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Boundary coverage for [LmdbConnection] beyond the happy-path listing/paging in
 * [LmdbConnectionTest]: environment stats, empty DBIs, exact-limit paging, exhausted continuation,
 * binary key prefixes, and the missing-DBI error path. Uses [TestEnvs] for fixtures.
 */
class LmdbConnectionEdgeCasesTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = TestEnvs.newTempDir()
        TestEnvs.populateDir(
            dir,
            "main",
            mapOf("a" to "1", "b" to "2", "c" to "3"),
        )
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun statsReportPopulatedFields() {
        TestEnvs.openReadOnly(dir).use { conn ->
            val stats = conn.stats()
            assertTrue("map size should be positive", stats.mapSize > 0)
            assertTrue("page size should be positive", stats.pageSize > 0)
            assertTrue(stats.maxReaders > 0)
            // One named DBI ("main") + the unnamed/main database.
            assertEquals(2, stats.dbiCount)
            assertEquals(dir.absolutePath, stats.path)
        }
    }

    @Test
    fun emptyDbiYieldsEmptyPageWithNoContinuation() {
        val emptyDir = TestEnvs.newTempDir()
        try {
            TestEnvs.createEmptyDbi(emptyDir, "blank")
            TestEnvs.openReadOnly(emptyDir).use { conn ->
                val page = conn.readPage("blank")
                assertTrue(page.entries.isEmpty())
                assertNull(page.nextKey)
                assertFalse(page.hasMore)
            }
        } finally {
            emptyDir.deleteRecursively()
        }
    }

    @Test
    fun limitEqualToSizeHasNoNextPage() {
        TestEnvs.openReadOnly(dir).use { conn ->
            val page = conn.readPage("main", limit = 3) // exactly the row count
            assertEquals(3, page.entries.size)
            assertNull(page.nextKey)
            assertFalse(page.hasMore)
        }
    }

    @Test
    fun limitOfOnePagesThroughEveryRow() {
        TestEnvs.openReadOnly(dir).use { conn ->
            val keys = ArrayList<String>()
            var after: ByteArray? = null
            do {
                val page = conn.readPage("main", afterKey = after, limit = 1)
                page.entries.forEach { keys += String(it.key) }
                after = page.nextKey
            } while (after != null)
            assertEquals(listOf("a", "b", "c"), keys)
        }
    }

    @Test
    fun afterKeyBeyondLastReturnsEmptyPage() {
        TestEnvs.openReadOnly(dir).use { conn ->
            val page = conn.readPage("main", afterKey = "z".b())
            assertTrue(page.entries.isEmpty())
            assertNull(page.nextKey)
        }
    }

    @Test
    fun binaryPrefixSelectsOnlyMatchingKeys() {
        val binDir = TestEnvs.newTempDir()
        try {
            TestEnvs.populateDirBytes(
                binDir,
                "main",
                mapOf(
                    byteArrayOf(0x01, 0x01) to byteArrayOf(0x10),
                    byteArrayOf(0xFF.toByte(), 0x01) to byteArrayOf(0x20),
                    byteArrayOf(0xFF.toByte(), 0x02) to byteArrayOf(0x30),
                ),
            )
            TestEnvs.openReadOnly(binDir).use { conn ->
                val page = conn.readPage("main", prefix = byteArrayOf(0xFF.toByte()))
                assertEquals(2, page.entries.size)
                assertTrue(page.entries.all { (it.key[0].toInt() and 0xFF) == 0xFF })
            }
        } finally {
            binDir.deleteRecursively()
        }
    }

    @Test
    fun readingMissingDbiThrows() {
        TestEnvs.openReadOnly(dir).use { conn ->
            try {
                conn.readPage("does-not-exist")
                fail("expected reading a non-existent DBI to throw")
            } catch (_: Exception) {
                // expected: a read-only env cannot open an unknown DBI (no MDB_CREATE).
            }
        }
    }
}
