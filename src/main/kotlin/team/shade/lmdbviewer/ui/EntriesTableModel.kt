package team.shade.lmdbviewer.ui

import team.shade.lmdbviewer.lmdb.LmdbEntry
import javax.swing.table.AbstractTableModel

/** Backs the entries [com.intellij.ui.table.JBTable]. Rows are appended as pages load. */
class EntriesTableModel : AbstractTableModel() {

    /** Previews are computed once per row at load time, not on every cell repaint. */
    private class Row(val entry: LmdbEntry) {
        val keyPreview: String = Previews.preview(entry.key)
        val valuePreview: String = Previews.preview(entry.value)
    }

    private val rows = ArrayList<Row>()

    fun reset(entries: List<LmdbEntry>) {
        rows.clear()
        entries.mapTo(rows) { Row(it) }
        fireTableDataChanged()
    }

    fun append(entries: List<LmdbEntry>) {
        if (entries.isEmpty()) return
        val from = rows.size
        entries.mapTo(rows) { Row(it) }
        fireTableRowsInserted(from, rows.size - 1)
    }

    fun entryAt(row: Int): LmdbEntry? = rows.getOrNull(row)?.entry

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 3

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "Key"
        1 -> "Value"
        2 -> "Size"
        else -> ""
    }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 2) java.lang.Integer::class.java else String::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.keyPreview
            1 -> row.valuePreview
            2 -> row.entry.valueSize
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
}
