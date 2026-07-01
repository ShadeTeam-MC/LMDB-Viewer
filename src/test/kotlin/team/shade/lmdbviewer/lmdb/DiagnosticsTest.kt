package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lmdbjava.ByteArrayProxy
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import java.io.File

/**
 * Diagnostics surface: DBI flags are now read back (fixing the always-empty flags / dead
 * `[DUPSORT]` marker), per-DBI B-tree stats are populated, `checkStaleReaders` works, and the
 * derived [EnvStats] getters are sane.
 */
class DiagnosticsTest {

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
    fun dupsortFlagIsReportedForDupsortDbi() {
        populateDupsort("colors", "warm", listOf("red", "orange", "yellow"))
        TestEnvs.openReadOnly(dir).use { conn ->
            val colors = conn.listDatabases().first { it.name == "colors" }
            assertTrue("DUPSORT should be reported", colors.isDupSort)
            assertTrue("DUPSORT" in colors.flags)
        }
    }

    @Test
    fun plainDbiHasNoDupsortFlag() {
        TestEnvs.populateDir(dir, "main", mapOf("a" to "1", "b" to "2"))
        TestEnvs.openReadOnly(dir).use { conn ->
            val main = conn.listDatabases().first { it.name == "main" }
            assertTrue("plain DBI must not be DUPSORT", !main.isDupSort)
        }
    }

    @Test
    fun perDbiStatsArePopulated() {
        TestEnvs.populateDir(dir, "main", (1..50).associate { "k%03d".format(it) to "v$it" })
        TestEnvs.openReadOnly(dir).use { conn ->
            val main = conn.listDatabases().first { it.name == "main" }
            assertEquals(50L, main.entryCount)
            assertTrue("depth should be at least 1", main.depth >= 1)
            assertTrue("a non-empty DBI has at least one leaf page", main.leafPages >= 1)
            assertTrue("total pages includes leaves", main.totalPages >= main.leafPages)
        }
    }

    @Test
    fun checkStaleReadersReturnsNonNegative() {
        TestEnvs.populateDir(dir, "main", mapOf("a" to "1"))
        TestEnvs.openReadOnly(dir).use { conn ->
            assertTrue(conn.checkStaleReaders() >= 0)
        }
    }

    @Test
    fun envStatsDerivedGettersAreSane() {
        TestEnvs.populateDir(dir, "main", mapOf("a" to "1", "b" to "2"))
        TestEnvs.openReadOnly(dir).use { conn ->
            val s = conn.stats()
            assertTrue("used bytes within map", s.usedBytes in 1..s.mapSize)
            assertTrue("utilization is a percentage", s.utilizationPercent in 0.0..100.0)
        }
    }

    /** Creates a DUPSORT DBI in [dir] with several values under one key, then closes the env. */
    private fun populateDupsort(dbiName: String, key: String, values: List<String>) {
        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(10).setMapSize(8L shl 20).open(dir).use { env ->
            val dbi = env.openDbi(dbiName, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT)
            env.txnWrite().use { txn ->
                values.forEach { dbi.put(txn, key.b(), it.b()) }
                txn.commit()
            }
        }
    }
}
