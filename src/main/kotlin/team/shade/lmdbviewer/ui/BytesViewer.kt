package team.shade.lmdbviewer.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import team.shade.lmdbviewer.decode.ByteDecoder
import team.shade.lmdbviewer.decode.DecoderRegistry
import java.awt.BorderLayout
import java.awt.Dimension
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
        // "Auto" shows the currently auto-detected type, e.g. "Auto (HEX)"; specific choices use their name.
        decoderCombo.renderer = SimpleListCellRenderer.create("") { labelFor(it) }
        decoderCombo.addActionListener { render() }
        decoderCombo.toolTipText = "Choose how these bytes are decoded; Auto picks the best format."
        widenComboToFitLabels()

        val copyButton = JButton("Copy").apply {
            toolTipText = "Copy the decoded text to the clipboard (Ctrl+C)."
            addActionListener { copyToClipboard() }
        }

        // Ctrl+C anywhere in this pane copies the decoded text (or the current selection, if any).
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = copyToClipboard()
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("control C"), this)

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

    /** Copies the current text selection if there is one, otherwise the whole decoded text. */
    private fun copyToClipboard() {
        val text = textArea.selectedText?.takeIf { it.isNotEmpty() } ?: textArea.text
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    fun show(bytes: ByteArray?) {
        current = bytes
        sizeLabel.text = if (bytes == null) "" else "${bytes.size} B   "
        render()
        decoderCombo.repaint() // refresh the collapsed "Auto (…)" label for the new bytes
    }

    /** Combo label for a choice: "Auto"/"Auto (TYPE)" for auto-detect, the decoder name otherwise. */
    private fun labelFor(choice: DecoderChoice?): String = when (choice) {
        is DecoderChoice.Specific -> choice.decoder.displayName
        else -> autoLabel()
    }

    private fun autoLabel(): String {
        val bytes = current ?: return "Auto"
        val detected = registry.autoDetect(bytes) ?: return "Auto"
        return "Auto (${detected.displayName.uppercase()})"
    }

    /** Sizes the combo to the widest label it can show (incl. "Auto (<TYPE>)"), so nothing is clipped. */
    private fun widenComboToFitLabels() {
        val font = decoderCombo.font ?: return
        val fm = decoderCombo.getFontMetrics(font)
        val labels = buildList {
            add("Auto")
            registry.decoders.forEach {
                add(it.displayName)
                add("Auto (${it.displayName.uppercase()})")
            }
        }
        val textWidth = labels.maxOf { fm.stringWidth(it) }
        // Room for the dropdown arrow + insets.
        val width = textWidth + JBUI.scale(44)
        decoderCombo.preferredSize = Dimension(width, decoderCombo.preferredSize.height)
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
