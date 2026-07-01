package team.shade.lmdbviewer.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import team.shade.lmdbviewer.lmdb.DbiInfo
import team.shade.lmdbviewer.lmdb.EnvStats
import team.shade.lmdbviewer.lmdb.LmdbConnection
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Read-only diagnostics for one environment: an environment summary plus a per-DBI B-tree statistics
 * table, and a button to clear stale reader-lock slots. Data is read on a background thread before
 * the dialog opens (see [LmdbViewerPanel.openDiagnostics]); only [checkStaleReaders] runs live.
 */
internal class LmdbDiagnosticsDialog(
    project: Project,
    private val connection: LmdbConnection,
    private val stats: EnvStats,
    private val dbis: List<DbiInfo>,
) : DialogWrapper(project) {

    private val readerResult = JBLabel(" ")

    init {
        title = "LMDB Diagnostics"
        setOKButtonText("Close")
        init()
    }

    // Info dialog: a single Close button (mapped to the OK action).
    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(760, 480)
        add(buildSummary(), BorderLayout.NORTH)
        add(JBScrollPane(buildDbiTable()), BorderLayout.CENTER)
        add(buildReaderRow(), BorderLayout.SOUTH)
    }

    private fun buildSummary(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Path:", JBLabel(stats.path))
        .addLabeledComponent("Map size:", JBLabel(StringUtil.formatFileSize(stats.mapSize)))
        .addLabeledComponent(
            "Used:",
            JBLabel("${StringUtil.formatFileSize(stats.usedBytes)} (${"%.1f".format(stats.utilizationPercent)}%)"),
        )
        .addLabeledComponent("Page size:", JBLabel("${stats.pageSize} B"))
        .addLabeledComponent("Readers:", JBLabel("${stats.numReaders} / ${stats.maxReaders}"))
        .addLabeledComponent("Last transaction id:", JBLabel(stats.lastTransactionId.toString()))
        .addLabeledComponent("Databases:", JBLabel(stats.dbiCount.toString()))
        .panel
        .apply { border = JBUI.Borders.empty(4, 4, 8, 4) }

    private fun buildDbiTable(): JBTable {
        val columns = arrayOf("Name", "Entries", "Depth", "Branch", "Leaf", "Overflow", "Total pages", "~Size", "Flags")
        val rows = dbis.map { d ->
            arrayOf<Any>(
                d.displayName,
                if (d.entryCount >= 0) d.entryCount else "?",
                d.depth,
                d.branchPages,
                d.leafPages,
                d.overflowPages,
                d.totalPages,
                StringUtil.formatFileSize(d.totalPages * stats.pageSize),
                d.flags.sorted().joinToString(", "),
            )
        }.toTypedArray()
        val model = object : DefaultTableModel(rows, columns) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        return JBTable(model).apply {
            autoResizeMode = JBTable.AUTO_RESIZE_OFF
            setShowGrid(true)
        }
    }

    private fun buildReaderRow(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
        add(JButton("Check stale readers").apply {
            toolTipText = "Release reader-lock slots left by processes/threads that exited without closing a read transaction."
            addActionListener { checkStaleReaders() }
        })
        add(readerResult)
    }

    private fun checkStaleReaders() {
        readerResult.text = "Checking…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val text = try {
                val n = connection.checkStaleReaders()
                "$n stale reader${if (n == 1) "" else "s"} cleared"
            } catch (t: Throwable) {
                "Failed: ${t.message}"
            }
            ApplicationManager.getApplication().invokeLater { readerResult.text = text }
        }
    }
}
