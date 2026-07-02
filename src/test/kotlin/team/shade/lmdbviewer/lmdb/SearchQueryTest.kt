package team.shade.lmdbviewer.lmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure matching logic for [ByteSearch] and [SearchQuery] — no LMDB needed. */
class SearchQueryTest {

    @Test
    fun startsWithMatchesPrefixAndEmpty() {
        assertTrue(ByteSearch.startsWith("hello".b(), "he".b()))
        assertTrue(ByteSearch.startsWith("hello".b(), "".b())) // empty prefix always matches
        assertFalse(ByteSearch.startsWith("hello".b(), "lo".b()))
        assertFalse(ByteSearch.startsWith("he".b(), "hello".b())) // longer than value
    }

    @Test
    fun indexOfFindsAtStartMiddleEnd() {
        assertEquals(0, ByteSearch.indexOf("hello".b(), "he".b()))
        assertEquals(2, ByteSearch.indexOf("hello".b(), "ll".b()))
        assertEquals(4, ByteSearch.indexOf("hello".b(), "o".b()))
    }

    @Test
    fun indexOfMissingAndEdgeCases() {
        assertEquals(-1, ByteSearch.indexOf("hello".b(), "xyz".b()))
        assertEquals(0, ByteSearch.indexOf("hello".b(), "".b()))   // empty needle -> 0
        assertEquals(-1, ByteSearch.indexOf("hi".b(), "hello".b())) // needle longer than haystack
        assertEquals(0, ByteSearch.indexOf("".b(), "".b()))
    }

    @Test
    fun indexOfWorksOnBinaryBytes() {
        val haystack = byteArrayOf(0x00, 0x0f, 0x00, 0xff.toByte())
        assertEquals(1, ByteSearch.indexOf(haystack, byteArrayOf(0x0f, 0x00)))
        assertEquals(-1, ByteSearch.indexOf(haystack, byteArrayOf(0x0f, 0x0f)))
    }

    @Test
    fun matchesRoutesByScope() {
        val entry = LmdbEntry("user:42".b(), "alice".b())
        assertTrue(SearchQuery(SearchScope.KEY_PREFIX, "user:".b()).matches(entry))
        assertFalse(SearchQuery(SearchScope.KEY_PREFIX, "42".b()).matches(entry)) // prefix, not contains
        assertTrue(SearchQuery(SearchScope.KEY_CONTAINS, "42".b()).matches(entry))
        assertTrue(SearchQuery(SearchScope.VALUE_CONTAINS, "lic".b()).matches(entry))
        assertFalse(SearchQuery(SearchScope.VALUE_CONTAINS, "user".b()).matches(entry))
    }

    @Test
    fun equalsAndHashCodeUseContent() {
        val a = SearchQuery(SearchScope.VALUE_CONTAINS, "x".b())
        val b = SearchQuery(SearchScope.VALUE_CONTAINS, "x".b())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == SearchQuery(SearchScope.KEY_CONTAINS, "x".b()))
    }
}
