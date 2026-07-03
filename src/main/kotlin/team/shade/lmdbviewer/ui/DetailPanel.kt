package team.shade.lmdbviewer.ui

import com.intellij.ui.OnePixelSplitter
import team.shade.lmdbviewer.decode.DecoderRegistry
import team.shade.lmdbviewer.lmdb.LmdbEntry
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * Bottom detail area: decoded views of the selected entry's key and value. For a DUPSORT database it
 * also shows a [DuplicatesPanel] listing every value of the selected key, with add/edit/remove.
 */
class DetailPanel(registry: DecoderRegistry) : JPanel(BorderLayout()) {

    private val keyViewer = BytesViewer("Key", registry)
    private val valueViewer = BytesViewer("Value", registry)
    private val duplicatesPanel = DuplicatesPanel()

    private val keyValueSplit = OnePixelSplitter(false, 0.4f).apply {
        firstComponent = keyViewer
        secondComponent = valueViewer
    }
    private val vertical = OnePixelSplitter(true, 0.6f).apply {
        firstComponent = keyValueSplit
        secondComponent = null // duplicates section is added only for DUPSORT DBIs
    }

    /** The value of the currently shown entry, restored in the value viewer when no duplicate is picked. */
    private var currentValue: ByteArray? = null

    init {
        // Selecting a value in the duplicates list previews it in the value viewer.
        duplicatesPanel.onSelect = { value -> valueViewer.show(value ?: currentValue) }
        add(vertical, BorderLayout.CENTER)
        showEntry(null)
    }

    /** Wires the duplicate action buttons to the owner (LMDB access lives there). */
    fun setDuplicateActions(
        onAdd: (key: ByteArray) -> Unit,
        onEdit: (key: ByteArray, oldValue: ByteArray) -> Unit,
        onRemove: (key: ByteArray, value: ByteArray) -> Unit,
    ) {
        duplicatesPanel.onAdd = onAdd
        duplicatesPanel.onEdit = onEdit
        duplicatesPanel.onRemove = onRemove
    }

    fun showEntry(entry: LmdbEntry?) {
        currentValue = entry?.value
        keyViewer.show(entry?.key)
        valueViewer.show(entry?.value)
    }

    /**
     * Shows (or hides) the duplicates section. Pass [key] == null to hide it (non-DUPSORT DBI or no
     * selection); otherwise it lists [values] for that key, with buttons enabled when [editable].
     */
    fun showDuplicates(key: ByteArray?, values: List<ByteArray>, editable: Boolean) {
        if (key == null) {
            vertical.secondComponent = null
        } else {
            duplicatesPanel.setData(key, values, editable)
            vertical.secondComponent = duplicatesPanel
        }
    }
}
