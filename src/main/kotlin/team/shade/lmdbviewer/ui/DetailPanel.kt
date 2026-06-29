package team.shade.lmdbviewer.ui

import com.intellij.ui.OnePixelSplitter
import team.shade.lmdbviewer.decode.DecoderRegistry
import team.shade.lmdbviewer.lmdb.LmdbEntry
import javax.swing.JPanel
import java.awt.BorderLayout

/** Bottom detail area: decoded views of the selected entry's key and value. */
class DetailPanel(registry: DecoderRegistry) : JPanel(BorderLayout()) {

    private val keyViewer = BytesViewer("Key", registry)
    private val valueViewer = BytesViewer("Value", registry)

    init {
        val splitter = OnePixelSplitter(false, 0.4f).apply {
            firstComponent = keyViewer
            secondComponent = valueViewer
        }
        add(splitter, BorderLayout.CENTER)
        showEntry(null)
    }

    fun showEntry(entry: LmdbEntry?) {
        keyViewer.show(entry?.key)
        valueViewer.show(entry?.value)
    }
}
