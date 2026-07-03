package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** [LmdbConnection.getDuplicates] and DUPSORT [MutationOps.replace] behaviour. */
class DuplicatesTest {

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

    private fun valuesOf(key: String) = conn!!.getDuplicates("db", key.b()).map { String(it) }

    @Test
    fun getDuplicatesReturnsAllValuesSorted() {
        TestEnvs.populateDupSort(dir, "db", mapOf("k" to listOf("c", "a", "b"), "other" to listOf("z")))
        conn = TestEnvs.openReadOnly(dir)
        assertEquals(listOf("a", "b", "c"), valuesOf("k")) // DUPSORT stores values sorted
        assertEquals(listOf("z"), valuesOf("other"))
    }

    @Test
    fun getDuplicatesEmptyForMissingKey() {
        TestEnvs.populateDupSort(dir, "db", mapOf("k" to listOf("a")))
        conn = TestEnvs.openReadOnly(dir)
        assertTrue(conn!!.getDuplicates("db", "missing".b()).isEmpty())
    }

    @Test
    fun getDuplicatesRespectsLimit() {
        TestEnvs.populateDupSort(dir, "db", mapOf("k" to listOf("a", "b", "c", "d")))
        conn = TestEnvs.openReadOnly(dir)
        assertEquals(2, conn!!.getDuplicates("db", "k".b(), limit = 2).size)
    }

    @Test
    fun replaceEditsOneDuplicateLeavingOthers() {
        TestEnvs.populateDupSort(dir, "db", mapOf("k" to listOf("a", "b", "c")))
        conn = TestEnvs.openWritable(dir)
        conn!!.mutations.replace("db", "k".b(), "b".b(), "z".b())
        // "b" replaced by "z"; "a" and "c" untouched; result stays sorted.
        assertEquals(listOf("a", "c", "z"), valuesOf("k"))
    }

    @Test
    fun replaceOnNormalDbiOverwrites() {
        TestEnvs.populateDir(dir, "db", mapOf("k" to "old"))
        conn = TestEnvs.openWritable(dir)
        conn!!.mutations.replace("db", "k".b(), "old".b(), "new".b())
        assertEquals(listOf("new"), valuesOf("k"))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun replaceRejectedOnReadOnly() {
        TestEnvs.populateDir(dir, "db", mapOf("k" to "v"))
        conn = TestEnvs.openReadOnly(dir)
        conn!!.mutations.replace("db", "k".b(), "v".b(), "x".b())
    }
}
