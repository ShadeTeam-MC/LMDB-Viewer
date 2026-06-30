package team.shade.lmdbviewer.lmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests for the plain data models: content-based identity and computed properties. */
class ModelsTest {

    @Test
    fun lmdbEntryEqualsAndHashCodeByContent() {
        val a = LmdbEntry(byteArrayOf(1, 2), byteArrayOf(3))
        val b = LmdbEntry(byteArrayOf(1, 2), byteArrayOf(3)) // distinct arrays, same content
        val c = LmdbEntry(byteArrayOf(1, 2), byteArrayOf(4))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)

        assertEquals(2, a.keySize)
        assertEquals(1, a.valueSize)
    }

    @Test
    fun entryPageHasMoreReflectsNextKey() {
        assertFalse(EntryPage(emptyList(), null).hasMore)
        assertTrue(EntryPage(emptyList(), byteArrayOf(0)).hasMore)
    }

    @Test
    fun dbiInfoComputedProperties() {
        val main = DbiInfo(name = null, entryCount = 0, flags = emptySet())
        assertEquals("(main)", main.displayName)
        assertFalse(main.isDupSort)

        val named = DbiInfo(name = "users", entryCount = 5, flags = setOf("DUPSORT"))
        assertEquals("users", named.displayName)
        assertTrue(named.isDupSort)
    }
}
