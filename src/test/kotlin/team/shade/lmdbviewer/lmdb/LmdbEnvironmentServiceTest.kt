package team.shade.lmdbviewer.lmdb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers [LmdbEnvironmentService] — the cache + path-resolution layer the UI uses to obtain
 * connections. All connections are closed via [LmdbEnvironmentService.dispose] in teardown before
 * any temp file is deleted (on Windows the mmap files stay locked while the env is open).
 */
class LmdbEnvironmentServiceTest {

    private lateinit var service: LmdbEnvironmentService
    private lateinit var dir: File
    private val cleanup = mutableListOf<File>()

    @Before
    fun setUp() {
        service = LmdbEnvironmentService()
        dir = track(TestEnvs.newTempDir())
        TestEnvs.populateDir(dir, "main", mapOf("k" to "v"))
    }

    @After
    fun tearDown() {
        service.dispose() // close every env first, then it is safe to delete the files
        cleanup.forEach { f ->
            f.deleteRecursively()
            File(f.path + "-lock").delete() // single-file envs leave a sibling lock file
        }
    }

    @Test
    fun openReturnsCachedConnectionForSamePath() {
        val first = service.open(dir.absolutePath)
        val second = service.open(dir.absolutePath)
        assertSame("second open should hit the cache", first, second)
    }

    @Test
    fun directoryAndDataMdbResolveToSameConnection() {
        val viaDir = service.open(dir.absolutePath)
        val viaDataFile = service.open(File(dir, "data.mdb").absolutePath)
        assertSame(viaDir, viaDataFile)
    }

    @Test
    fun singleFileEnvironmentOpens() {
        val file = track(TestEnvs.newTempFile())
        TestEnvs.populateSingleFile(file, "main", mapOf("x" to "y"))

        val conn = service.open(file.absolutePath)
        assertNotNull(conn)
        val page = conn.readPage("main")
        assertTrue(page.entries.any { String(it.key) == "x" })
    }

    @Test
    fun badPathThrowsLmdbOpenException() {
        val missing = File(dir.parentFile, "definitely-not-here-${dir.name}.mdb").absolutePath
        try {
            service.open(missing)
            fail("expected LmdbOpenException for a non-existent environment")
        } catch (e: LmdbOpenException) {
            assertTrue(e.message!!.contains("Failed to open"))
        }
    }

    @Test
    fun openOrNullSwallowsFailure() {
        val missing = File(dir.parentFile, "missing-${dir.name}.mdb").absolutePath
        assertNull(service.openOrNull(missing))
    }

    @Test
    fun closeRemovesFromCacheAndReopenCreatesNew() {
        val first = service.open(dir.absolutePath)
        assertTrue(service.openConnections().isNotEmpty())

        service.close(dir.absolutePath)
        assertTrue(service.openConnections().isEmpty())

        val reopened = service.open(dir.absolutePath)
        org.junit.Assert.assertNotSame(first, reopened)
    }

    @Test
    fun disposeClosesAllAndClearsCache() {
        service.open(dir.absolutePath)
        assertTrue(service.openConnections().isNotEmpty())
        service.dispose()
        assertTrue(service.openConnections().isEmpty())
    }

    @Test
    fun openWritableReturnsWritableConnection() {
        val conn = service.open(dir.absolutePath, writable = true)
        assertTrue(conn.writable)
    }

    @Test
    fun togglingModeReopensWithDifferentConnection() {
        val readOnly = service.open(dir.absolutePath)
        assertFalse(readOnly.writable)

        val writable = service.open(dir.absolutePath, writable = true)
        assertNotSame("mode change must reopen", readOnly, writable)
        assertTrue(writable.writable)
        // The cache now holds the writable connection for this path.
        assertSame(writable, service.open(dir.absolutePath, writable = true))

        val readOnlyAgain = service.open(dir.absolutePath, writable = false)
        assertNotSame(writable, readOnlyAgain)
        assertFalse(readOnlyAgain.writable)
    }

    @Test
    fun writableConnectionFromServiceAcceptsWrites() {
        // Production path: obtain a writable connection from the service and mutate through it.
        val freshDir = track(TestEnvs.newTempDir())
        val conn = service.open(freshDir.absolutePath, writable = true)
        conn.mutations.put(null, "k".b(), "v".b()) // unnamed/main DBI always exists
        val read = conn.readPage(null).entries.first { String(it.key) == "k" }.value
        assertEquals("v", String(read))
    }

    private fun track(f: File): File = f.also { cleanup += it }
}
