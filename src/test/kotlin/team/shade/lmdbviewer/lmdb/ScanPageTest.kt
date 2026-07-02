package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Exercises [LmdbConnection.scanPage]: content search, pagination continuity and edge cases. */
class ScanPageTest {

    private lateinit var dir: File
    private var conn: LmdbConnection? = null

    @Before
    fun setUp() {
        dir = TestEnvs.newTempDir()
    }

    @After
    fun tearDown() {
        conn?.close()
        dir.deleteRecursively()
    }

    /** Scans an entire DBI in one call by using a limit larger than the DBI. */
    private fun scanAll(query: SearchQuery): List<LmdbEntry> =
        conn!!.scanPage("db", query, limit = 1000).entries

    @Test
    fun findsByValueSubstring() {
        TestEnvs.populateDir(dir, "db", mapOf("k1" to "alice", "k2" to "bob", "k3" to "malice"))
        conn = TestEnvs.openReadOnly(dir)
        val hits = scanAll(SearchQuery(SearchScope.VALUE_CONTAINS, "lic".b()))
        assertEquals(setOf("k1", "k3"), hits.map { String(it.key) }.toSet())
    }

    @Test
    fun findsByKeySubstring() {
        TestEnvs.populateDir(dir, "db", mapOf("user:1" to "a", "acct:2" to "b", "user:3" to "c"))
        conn = TestEnvs.openReadOnly(dir)
        val hits = scanAll(SearchQuery(SearchScope.KEY_CONTAINS, "ser".b()))
        assertEquals(setOf("user:1", "user:3"), hits.map { String(it.key) }.toSet())
    }

    @Test
    fun paginationCoversAllMatchesWithoutGapsOrDuplicates() {
        // 10 matching values (v0..v9) interleaved with non-matching ones.
        val entries = (0 until 10).flatMap { i ->
            listOf("m%02d".format(i) to "match-$i", "n%02d".format(i) to "nope-$i")
        }.toMap()
        TestEnvs.populateDir(dir, "db", entries)
        conn = TestEnvs.openReadOnly(dir)

        val query = SearchQuery(SearchScope.VALUE_CONTAINS, "match".b())
        val collected = ArrayList<String>()
        var after: ByteArray? = null
        var pages = 0
        do {
            val page = conn!!.scanPage("db", query, afterKey = after, limit = 3)
            page.entries.forEach { collected += String(it.value) }
            after = page.nextKey
            pages++
        } while (after != null && pages < 100)

        assertEquals((0 until 10).map { "match-$it" }, collected) // ordered, complete, no dups
        assertTrue("expected multiple pages", pages >= 4)
    }

    @Test
    fun binaryNeedleMatchesValueBytes() {
        TestEnvs.populateDirBytes(
            dir, "db",
            mapOf("a".b() to byteArrayOf(0x00, 0x0f, 0x10), "b".b() to byteArrayOf(0x20, 0x21)),
        )
        conn = TestEnvs.openReadOnly(dir)
        val hits = scanAll(SearchQuery(SearchScope.VALUE_CONTAINS, byteArrayOf(0x0f, 0x10)))
        assertEquals(listOf("a"), hits.map { String(it.key) })
    }

    @Test
    fun noMatchYieldsEmptyPageWithNullContinuation() {
        TestEnvs.populateDir(dir, "db", mapOf("k1" to "alice", "k2" to "bob"))
        conn = TestEnvs.openReadOnly(dir)
        val page = conn!!.scanPage("db", SearchQuery(SearchScope.VALUE_CONTAINS, "zzz".b()))
        assertTrue(page.entries.isEmpty())
        assertNull(page.nextKey)
    }
}
