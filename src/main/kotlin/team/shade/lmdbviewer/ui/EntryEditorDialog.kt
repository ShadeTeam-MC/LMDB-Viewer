package team.shade.lmdbviewer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Collects a key and/or value (as raw bytes) for an add/edit operation. Each editable field offers
 * a UTF-8 / Hex toggle via [ByteCodec]; switching re-encodes the current contents when possible.
 *
 * Use [forAdd] to enter a brand-new key+value, or [forEditValue] to change the value of an existing
 * key (the key is shown read-only). Read [resultKey] / [resultValue] after [showAndGet] returns true.
 */
internal class EntryEditorDialog private constructor(
    project: Project,
    dialogTitle: String,
    private val keyInput: BytesInput?,
    private val fixedKey: ByteArray?,
    private val keyDisplay: String?,
    private val valueInput: BytesInput,
) : DialogWrapper(project) {

    var resultKey: ByteArray? = null
        private set
    var resultValue: ByteArray? = null
        private set

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(560, 360)

        if (keyInput != null) {
            add(keyInput)
        } else {
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(4)
                add(JBLabel("Key (read-only):"), BorderLayout.NORTH)
                add(
                    JBScrollPane(JBTextArea(keyDisplay ?: "").apply { isEditable = false; lineWrap = true }),
                    BorderLayout.CENTER,
                )
            })
        }
        add(valueInput)
    }

    override fun doValidate(): ValidationInfo? {
        if (keyInput != null && keyInput.bytes() == null) {
            return ValidationInfo("Key is not valid hex", keyInput.editor)
        }
        if (valueInput.bytes() == null) {
            return ValidationInfo("Value is not valid hex", valueInput.editor)
        }
        return null
    }

    override fun doOKAction() {
        resultKey = keyInput?.bytes() ?: fixedKey
        resultValue = valueInput.bytes()
        super.doOKAction()
    }

    /** A labelled byte field: a text area plus a UTF-8/Hex encoding toggle. */
    class BytesInput(label: String, initial: ByteArray?) : JPanel(BorderLayout()) {

        private val area = JBTextArea(5, 40).apply { lineWrap = true }
        private val utf8 = JBRadioButton("UTF-8")
        private val hex = JBRadioButton("Hex")
        private var mode: ByteCodec.Mode = if (initial != null) ByteCodec.defaultMode(initial) else ByteCodec.Mode.UTF8

        /** The text component, for focusing on validation errors. */
        val editor: JComponent get() = area

        init {
            border = JBUI.Borders.empty(4)
            ButtonGroup().apply { add(utf8); add(hex) }
            utf8.isSelected = mode == ByteCodec.Mode.UTF8
            hex.isSelected = mode == ByteCodec.Mode.HEX
            if (initial != null) area.text = ByteCodec.format(initial, mode)
            utf8.addActionListener { switchTo(ByteCodec.Mode.UTF8) }
            hex.addActionListener { switchTo(ByteCodec.Mode.HEX) }

            val header = JPanel(BorderLayout()).apply {
                add(JBLabel(label), BorderLayout.WEST)
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { add(utf8); add(hex) }, BorderLayout.EAST)
            }
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(area), BorderLayout.CENTER)
        }

        /** Bytes for the current contents, or null if the text is invalid for the selected mode. */
        fun bytes(): ByteArray? = ByteCodec.parse(area.text, mode)

        private fun switchTo(newMode: ByteCodec.Mode) {
            if (newMode == mode) return
            // Convert the existing contents to the new encoding when they parse; otherwise leave them.
            ByteCodec.parse(area.text, mode)?.let { area.text = ByteCodec.format(it, newMode) }
            mode = newMode
        }
    }

    companion object {
        fun forAdd(project: Project): EntryEditorDialog = EntryEditorDialog(
            project,
            dialogTitle = "Add Entry",
            keyInput = BytesInput("Key:", null),
            fixedKey = null,
            keyDisplay = null,
            valueInput = BytesInput("Value:", null),
        )

        fun forEditValue(project: Project, key: ByteArray, value: ByteArray): EntryEditorDialog = EntryEditorDialog(
            project,
            dialogTitle = "Edit Value",
            keyInput = null,
            fixedKey = key,
            keyDisplay = ByteCodec.format(key, ByteCodec.defaultMode(key)),
            valueInput = BytesInput("Value:", value),
        )

        /** Adds another value under an existing (DUPSORT) key: key read-only, value blank. */
        fun forAddValue(project: Project, key: ByteArray): EntryEditorDialog = EntryEditorDialog(
            project,
            dialogTitle = "Add Duplicate Value",
            keyInput = null,
            fixedKey = key,
            keyDisplay = ByteCodec.format(key, ByteCodec.defaultMode(key)),
            valueInput = BytesInput("Value:", null),
        )
    }
}
