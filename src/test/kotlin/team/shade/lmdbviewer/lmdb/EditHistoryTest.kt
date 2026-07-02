package team.shade.lmdbviewer.lmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure stack behaviour of [EditHistory]. */
class EditHistoryTest {

    private fun put(k: String) = Mutation.Put(null, k.b(), "v".b())

    @Test
    fun recordsAndPopsLifo() {
        val h = EditHistory()
        assertFalse(h.canUndo)
        h.record(put("a"))
        h.record(put("b"))
        assertTrue(h.canUndo)
        assertEquals(2, h.size)
        assertEquals(put("b"), h.pop())
        assertEquals(put("a"), h.pop())
        assertNull(h.pop())
        assertFalse(h.canUndo)
    }

    @Test
    fun boundedLimitDropsOldest() {
        val h = EditHistory(limit = 3)
        listOf("a", "b", "c", "d").forEach { h.record(put(it)) }
        assertEquals(3, h.size)
        // "a" was evicted; newest-first the survivors are d, c, b.
        assertEquals(put("d"), h.pop())
        assertEquals(put("c"), h.pop())
        assertEquals(put("b"), h.pop())
        assertNull(h.pop())
    }

    @Test
    fun clearEmpties() {
        val h = EditHistory()
        h.record(put("a"))
        h.clear()
        assertFalse(h.canUndo)
        assertEquals(0, h.size)
    }
}
