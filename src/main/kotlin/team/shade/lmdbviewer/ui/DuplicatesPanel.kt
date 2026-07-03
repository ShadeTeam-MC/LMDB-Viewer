package team.shade.lmdbviewer.ui

import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Lists every value stored under one key of a DUPSORT database, with Add / Edit / Remove actions on
 * individual duplicates. The owner sets the [onAdd] / [onEdit] / [onRemove] / [onSelect] callbacks and
 * feeds data through [setData]; this panel holds no LMDB access itself.
 */
class DuplicatesPanel : JPanel(BorderLayout()) {

    var onAdd: (key: ByteArray) -> Unit = {}
    var onEdit: (key: ByteArray, oldValue: ByteArray) -> Unit = { _, _ -> }
    var onRemove: (key: ByteArray, value: ByteArray) -> Unit = { _, _ -> }

    /** Fired when the selected value changes (null when nothing is selected). */
    var onSelect: (value: ByteArray?) -> Unit = {}

    private val header = JBLabel("Values")
    private val model = DefaultListModel<ByteArray>()
    private val list = JBList(model)
    private val addButton = JButton("Add…")
    private val editButton = JButton("Edit…")
    private val removeButton = JButton("Remove")

    private var currentKey: ByteArray? = null
    private var editable = false

    init {
        border = JBUI.Borders.empty(4)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = SimpleListCellRenderer.create("∅") { Previews.preview(it) }
        list.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            onSelect(list.selectedValue)
            updateButtons()
        }

        addButton.apply {
            toolTipText = "Add another value under this key."
            addActionListener { currentKey?.let { onAdd(it) } }
        }
        editButton.apply {
            toolTipText = "Edit the selected value (replaces just this duplicate)."
            addActionListener { withSelection { key, value -> onEdit(key, value) } }
        }
        removeButton.apply {
            toolTipText = "Remove the selected value."
            addActionListener { withSelection { key, value -> onRemove(key, value) } }
        }

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(addButton); add(editButton); add(removeButton)
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(buttons, BorderLayout.SOUTH)
        updateButtons()
    }

    /** Populates the list with [values] for [key]; [editable] toggles the action buttons. */
    fun setData(key: ByteArray, values: List<ByteArray>, editable: Boolean) {
        currentKey = key
        this.editable = editable
        model.clear()
        values.forEach { model.addElement(it) }
        header.text = "Values (${values.size})"
        updateButtons()
    }

    private inline fun withSelection(action: (key: ByteArray, value: ByteArray) -> Unit) {
        val key = currentKey ?: return
        val value = list.selectedValue ?: return
        action(key, value)
    }

    private fun updateButtons() {
        val hasSelection = list.selectedValue != null
        addButton.isEnabled = editable && currentKey != null
        editButton.isEnabled = editable && hasSelection
        removeButton.isEnabled = editable && hasSelection
    }
}
