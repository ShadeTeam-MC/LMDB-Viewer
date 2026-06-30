package team.shade.lmdbviewer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import team.shade.lmdbviewer.lmdb.LmdbEntry
import java.nio.charset.StandardCharsets

/** Tests the Swing-independent logic of [EntriesTableModel]: row mutation, lookup, and cell values. */
class EntriesTableModelTest {

    private fun entry(key: String, value: String) =
        LmdbEntry(key.toByteArray(StandardCharsets.UTF_8), value.toByteArray(StandardCharsets.UTF_8))

    @Test
    fun resetReplacesRows() {
        val model = EntriesTableModel()
        model.reset(listOf(entry("a", "1"), entry("b", "2")))
        assertEquals(2, model.rowCount)

        model.reset(listOf(entry("c", "3")))
        assertEquals(1, model.rowCount)
        assertEquals("c", model.getValueAt(0, 0))
    }

    @Test
    fun appendAddsRowsAndEmptyAppendIsNoOp() {
        val model = EntriesTableModel()
        model.reset(listOf(entry("a", "1")))
        model.append(listOf(entry("b", "2"), entry("c", "3")))
        assertEquals(3, model.rowCount)

        model.append(emptyList())
        assertEquals(3, model.rowCount)
    }

    @Test
    fun entryAtReturnsRowOrNullOutOfBounds() {
        val model = EntriesTableModel()
        model.reset(listOf(entry("a", "1")))
        assertEquals("a", String(model.entryAt(0)!!.key))
        assertNull(model.entryAt(-1))
        assertNull(model.entryAt(5))
    }

    @Test
    fun getValueAtReturnsPreviewsAndSize() {
        val model = EntriesTableModel()
        model.reset(listOf(entry("key", "value")))
        assertEquals("key", model.getValueAt(0, 0))     // key preview
        assertEquals("value", model.getValueAt(0, 1))   // value preview
        assertEquals(5, model.getValueAt(0, 2))         // value size (Int)
    }

    @Test
    fun columnMetadata() {
        val model = EntriesTableModel()
        assertEquals(3, model.columnCount)
        assertEquals("Key", model.getColumnName(0))
        assertEquals("Value", model.getColumnName(1))
        assertEquals("Size", model.getColumnName(2))
        assertEquals(java.lang.Integer::class.java, model.getColumnClass(2))
        assertEquals(java.lang.String::class.java, model.getColumnClass(0))
        assertFalse(model.isCellEditable(0, 0))
    }
}
