package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * End-to-end reversibility: apply an edit through the write seam, then apply its computed inverse and
 * confirm the DBI is back to its prior state. Exercises [LmdbConnection.get] + [Inverses] + [applyTo].
 */
class UndoRoundTripTest {

    private lateinit var dir: File
    private var conn: LmdbConnection? = null

    @Before
    fun setUp() {
        dir = TestEnvs.newTempDir()
        TestEnvs.createEmptyDbi(dir, "db")
    }

    @After
    fun tearDown() {
        conn?.close()
        dir.deleteRecursively()
    }

    @Test
    fun getReturnsValueOrNull() {
        TestEnvs.populateDir(dir, "db", mapOf("k" to "v"))
        conn = TestEnvs.openWritable(dir)
        assertArrayEquals("v".b(), conn!!.get("db", "k".b()))
        assertNull(conn!!.get("db", "missing".b()))
    }

    @Test
    fun undoOfInsertRemovesKey() {
        conn = TestEnvs.openWritable(dir)
        val c = conn!!
        val prior = c.get("db", "k".b()) // null — key absent
        c.mutations.put("db", "k".b(), "v".b())
        Inverses.forPut("db", "k".b(), "v".b(), isDupSort = false, priorValue = prior).applyTo(c.mutations)
        assertNull(c.get("db", "k".b()))
    }

    @Test
    fun undoOfOverwriteRestoresPriorValue() {
        TestEnvs.populateDir(dir, "db", mapOf("k" to "old"))
        conn = TestEnvs.openWritable(dir)
        val c = conn!!
        val prior = c.get("db", "k".b()) // "old"
        c.mutations.put("db", "k".b(), "new".b())
        Inverses.forPut("db", "k".b(), "new".b(), isDupSort = false, priorValue = prior).applyTo(c.mutations)
        assertArrayEquals("old".b(), c.get("db", "k".b()))
    }

    @Test
    fun undoOfDeleteReAddsPair() {
        TestEnvs.populateDir(dir, "db", mapOf("k" to "v"))
        conn = TestEnvs.openWritable(dir)
        val c = conn!!
        c.mutations.delete("db", "k".b(), "v".b())
        assertNull(c.get("db", "k".b()))
        Inverses.forDelete("db", "k".b(), "v".b()).applyTo(c.mutations)
        assertArrayEquals("v".b(), c.get("db", "k".b()))
    }

    @Test
    fun undoOfDupSortReplaceRestoresPairLeavingOthers() {
        dir.deleteRecursively(); dir = TestEnvs.newTempDir()
        TestEnvs.populateDupSort(dir, "db", mapOf("k" to listOf("a", "b", "c")))
        conn = TestEnvs.openWritable(dir)
        val c = conn!!
        c.mutations.replace("db", "k".b(), "b".b(), "z".b())
        assertEquals(listOf("a", "c", "z"), c.getDuplicates("db", "k".b()).map { String(it) })
        Inverses.forReplace("db", "k".b(), "b".b(), "z".b()).applyTo(c.mutations)
        assertEquals(listOf("a", "b", "c"), c.getDuplicates("db", "k".b()).map { String(it) })
    }
}
