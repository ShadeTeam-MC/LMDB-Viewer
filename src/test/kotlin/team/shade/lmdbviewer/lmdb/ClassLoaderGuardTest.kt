package team.shade.lmdbviewer.lmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies [ClassLoaderGuard] installs the plugin classloader for the duration of the block and
 * always restores the previous context classloader — including when the block throws.
 */
class ClassLoaderGuardTest {

    private val pluginLoader: ClassLoader = ClassLoaderGuard::class.java.classLoader

    @Test
    fun installsPluginLoaderInsideAndRestoresAfter() {
        val thread = Thread.currentThread()
        val original = thread.contextClassLoader

        val seenInside = ClassLoaderGuard.runWithPluginClassLoader {
            thread.contextClassLoader
        }

        assertSame("plugin loader active inside the block", pluginLoader, seenInside)
        assertSame("original loader restored after the block", original, thread.contextClassLoader)
    }

    @Test
    fun restoresContextClassLoaderWhenBlockThrows() {
        val thread = Thread.currentThread()
        val original = thread.contextClassLoader
        try {
            ClassLoaderGuard.runWithPluginClassLoader {
                throw IllegalStateException("boom")
            }
            fail("exception should propagate")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertSame("loader restored even on exception", original, thread.contextClassLoader)
    }

    @Test
    fun returnsBlockResult() {
        val result = ClassLoaderGuard.runWithPluginClassLoader { 21 * 2 }
        assertEquals(42, result)
    }
}
