package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.lmdbjava.ByteArrayProxy
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import java.io.File

/**
 * Exercises the write path: [WritableMutationOps] put/delete against a writable environment, the
 * DUPSORT delete-by-value case, and the [ReadOnlyMutationOps] rejection. Each connection is closed
 * before the temp directory is removed.
 */
class WritableMutationOpsTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = TestEnvs.newTempDir()
        TestEnvs.populateDir(dir, "main", mapOf("a" to "1", "b" to "2"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun putAddsNewEntryVisibleToReads() {
        TestEnvs.openWritable(dir).use { conn ->
            conn.mutations.put("main", "c".b(), "3".b())
            val keys = conn.readPage("main").entries.map { String(it.key) }
            assertEquals(listOf("a", "b", "c"), keys)
        }
    }

    @Test
    fun putOverwritesExistingValue() {
        TestEnvs.openWritable(dir).use { conn ->
            conn.mutations.put("main", "a".b(), "updated".b())
            val value = conn.readPage("main").entries.first { String(it.key) == "a" }.value
            assertEquals("updated", String(value))
        }
    }

    @Test
    fun deleteRemovesEntry() {
        TestEnvs.openWritable(dir).use { conn ->
            conn.mutations.delete("main", "a".b(), null)
            val keys = conn.readPage("main").entries.map { String(it.key) }
            assertEquals(listOf("b"), keys)
        }
    }

    @Test
    fun deleteByValueRemovesOnlyThatDuplicateInDupsortDbi() {
        populateDupsort("colors", "warm", listOf("red", "orange", "yellow"))
        TestEnvs.openWritable(dir).use { conn ->
            conn.mutations.delete("colors", "warm".b(), "orange".b())
            val values = conn.readPage("colors").entries.map { String(it.value) }
            assertEquals(listOf("red", "yellow"), values) // only the targeted duplicate is gone
        }
    }

    @Test
    fun readOnlyConnectionRejectsWrites() {
        TestEnvs.openReadOnly(dir).use { conn ->
            assertFalse(conn.writable)
            try {
                conn.mutations.put("main", "x".b(), "y".b())
                fail("read-only connection must reject put")
            } catch (_: UnsupportedOperationException) {
            }
            try {
                conn.mutations.delete("main", "a".b(), null)
                fail("read-only connection must reject delete")
            } catch (_: UnsupportedOperationException) {
            }
        }
    }

    @Test
    fun writableConnectionReportsWritable() {
        TestEnvs.openWritable(dir).use { conn ->
            assertTrue(conn.writable)
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
