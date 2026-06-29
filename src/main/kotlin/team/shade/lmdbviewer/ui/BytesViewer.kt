package team.shade.lmdbviewer.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import team.shade.lmdbviewer.decode.ByteDecoder
import team.shade.lmdbviewer.decode.DecoderRegistry
import java.awt.BorderLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * Shows one byte array (a key or a value) with a decoder selector. "Auto" picks the best decoder via
 * [DecoderRegistry.autoDetect]; the user can override per pane.
 */
class BytesViewer(
    title: String,
    private val registry: DecoderRegistry,
) : JPanel(BorderLayout()) {

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = false
    }
    private val decoderCombo = JComboBox<DecoderChoice>()
    private val sizeLabel = JBLabel("")
    private var current: ByteArray? = null

    init {
        border = JBUI.Borders.empty(4)

        val choices = buildList {
            add(DecoderChoice.Auto)
            registry.decoders.forEach { add(DecoderChoice.Specific(it)) }
        }
        decoderCombo.model = DefaultComboBoxModel(choices.toTypedArray())
        decoderCombo.addActionListener { render() }

        val copyButton = JButton("Copy").apply {
            addActionListener {
                CopyPasteManager.getInstance().setContents(StringSelection(textArea.text))
            }
        }

        val header = JPanel(BorderLayout()).apply {
            add(JBLabel(title).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.WEST)
            val east = JPanel().apply {
                add(sizeLabel)
                add(decoderCombo)
                add(copyButton)
            }
            add(east, BorderLayout.EAST)
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    fun show(bytes: ByteArray?) {
        current = bytes
        sizeLabel.text = if (bytes == null) "" else "${bytes.size} B   "
        render()
    }

    private fun render() {
        val bytes = current
        if (bytes == null) {
            textArea.text = ""
            return
        }
        val decoder: ByteDecoder? = when (val choice = decoderCombo.selectedItem) {
            is DecoderChoice.Specific -> choice.decoder
            else -> registry.autoDetect(bytes)
        }
        val view = decoder?.let { runCatching { it.decode(bytes) }.getOrNull() }
        textArea.text = view?.text ?: "(no decoder)"
        textArea.font = if (view?.monospace != false) {
            Font(Font.MONOSPACED, Font.PLAIN, textArea.font.size)
        } else {
            JBUI.Fonts.label()
        }
        textArea.caretPosition = 0
    }

    private sealed interface DecoderChoice {
        object Auto : DecoderChoice {
            override fun toString() = "Auto"
        }

        class Specific(val decoder: ByteDecoder) : DecoderChoice {
            override fun toString() = decoder.displayName
        }
    }
}
