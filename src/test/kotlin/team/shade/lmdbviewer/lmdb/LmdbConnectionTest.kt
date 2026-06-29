package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lmdbjava.ByteArrayProxy
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Integration test against a real on-disk LMDB environment created with lmdbjava. Exercises the
 * read path: listing DBIs, paged iteration with continuation, and key-prefix scanning.
 *
 * Requires the JNR `--add-opens` flags (wired into the `test` task in build.gradle.kts).
 */
class LmdbConnectionTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File.createTempFile("lmdb-test", "").let {
            it.delete(); it.mkdirs(); it
        }
        // Populate a fixture env, then close it so we can reopen read-only.
        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(10).setMapSize(8L shl 20).open(dir).use { env ->
            val main = env.openDbi("main", DbiFlags.MDB_CREATE)
            val data = mapOf(
                "post:1" to "a",
                "user:1" to """{"id":1}""",
                "user:2" to "two",
                "user:3" to "three",
                "zeta" to "z",
            )
            env.txnWrite().use { txn ->
                data.forEach { (k, v) -> main.put(txn, k.bytes(), v.bytes()) }
                txn.commit()
            }
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun openReadOnly(): LmdbConnection {
        val env = Env.create(ByteArrayProxy.PROXY_BA)
            .setMaxDbs(10)
            .setMapSize(8L shl 20)
            .open(dir, org.lmdbjava.EnvFlags.MDB_RDONLY_ENV, org.lmdbjava.EnvFlags.MDB_NOTLS)
        return LmdbConnection(dir.absolutePath, env)
    }

    @Test
    fun listsMainAndNamedDatabases() {
        openReadOnly().use { conn ->
            val dbis = conn.listDatabases()
            assertTrue(dbis.any { it.name == null })       // main
            val named = dbis.firstOrNull { it.name == "main" }
            assertTrue(named != null && named.entryCount == 5L)
        }
    }

    @Test
    fun pagesEntriesWithContinuation() {
        openReadOnly().use { conn ->
            val page1 = conn.readPage("main", limit = 2)
            assertEquals(2, page1.entries.size)
            assertTrue(page1.hasMore)

            val page2 = conn.readPage("main", afterKey = page1.nextKey, limit = 2)
            val page3 = conn.readPage("main", afterKey = page2.nextKey, limit = 2)

            val keys = (page1.entries + page2.entries + page3.entries).map { String(it.key) }
            // LMDB sorts by byte order.
            assertEquals(listOf("post:1", "user:1", "user:2", "user:3", "zeta"), keys)
            assertNull(page3.nextKey)
        }
    }

    @Test
    fun prefixScanReturnsOnlyMatchingKeys() {
        openReadOnly().use { conn ->
            val page = conn.readPage("main", prefix = "user:".bytes())
            val keys = page.entries.map { String(it.key) }
            assertEquals(listOf("user:1", "user:2", "user:3"), keys)
        }
    }

    private fun String.bytes() = toByteArray(StandardCharsets.UTF_8)
}
