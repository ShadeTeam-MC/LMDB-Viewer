package team.shade.lmdbviewer.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import team.shade.lmdbviewer.decode.DecoderRegistry
import team.shade.lmdbviewer.lmdb.DbiInfo
import team.shade.lmdbviewer.lmdb.LmdbConnection
import team.shade.lmdbviewer.lmdb.LmdbEnvironmentService
import team.shade.lmdbviewer.settings.RecentEnvironmentsService
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * Main UI of the LMDB Viewer tool window: environment/DBI tree (left), paged entries table and a
 * decoded detail panel (right). All LMDB access happens on a pooled thread; the EDT only renders.
 */
class LmdbViewerPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = service<LmdbEnvironmentService>()
    private val recent = service<RecentEnvironmentsService>()
    private val registry = DecoderRegistryFactory.create()

    private val rootNode = DefaultMutableTreeNode("Environments")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    private val tableModel = EntriesTableModel()
    private val table = JBTable(tableModel)
    private val detailPanel = DetailPanel(registry)

    private val searchField = JBTextField()
    private val loadMoreButton = JButton("Load more")
    private val statusLabel = JBLabel(" ")

    // Paging state for the currently selected DBI.
    private var currentDbi: DbiNode? = null
    private var nextKey: ByteArray? = null
    private var loading = false

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)
        configureTree()
        configureTable()
        reloadRecentlyOpen()
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private fun buildToolbar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
        add(JButton("Open Environment…").apply { addActionListener { chooseAndOpen() } })
        add(JButton("Refresh").apply { addActionListener { refreshSelected() } })
        add(JButton("Close").apply { addActionListener { closeSelectedEnv() } })
        add(JBLabel("   Key prefix:"))
        searchField.columns = 18
        searchField.toolTipText = "Filter by key prefix. Plain text = UTF-8; prefix with 0x for hex (e.g. 0x00ff)."
        searchField.addActionListener { applySearch() }
        add(searchField)
        add(JButton("Find").apply { addActionListener { applySearch() } })
    }

    private fun buildBody(): OnePixelSplitter {
        val treeScroll = JBScrollPane(tree)

        val tablePanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                loadMoreButton.isEnabled = false
                loadMoreButton.addActionListener { loadNextPage() }
                add(loadMoreButton)
            }, BorderLayout.SOUTH)
        }

        val rightSplit = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = tablePanel
            secondComponent = detailPanel
        }

        return OnePixelSplitter(false, 0.28f).apply {
            firstComponent = treeScroll
            secondComponent = rightSplit
        }
    }

    private fun buildStatusBar(): JPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        statusLabel.horizontalAlignment = SwingConstants.LEFT
        add(statusLabel, BorderLayout.CENTER)
    }

    private fun configureTree() {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            (node.userObject as? DbiNode)?.let { selectDbi(it) }
        }
    }

    private fun configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.columnModel.getColumn(2).maxWidth = 90
        table.selectionModel.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            val entry = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.entryAt(it) }
            detailPanel.showEntry(entry)
        }
    }

    // ---- Actions --------------------------------------------------------------------------------

    fun chooseAndOpen() {
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false)
            .withTitle("Open LMDB Environment")
            .withDescription("Select an LMDB environment directory, a data.mdb file, or a single-file *.mdb store")
        val file = FileChooser.chooseFile(descriptor, project, null) ?: return
        openEnvironment(file.path)
    }

    fun openEnvironment(path: String) {
        setStatus("Opening $path …")
        runBg(
            work = {
                val connection = service.open(path)
                connection to connection.listDatabases()
            },
            onSuccess = { (connection, dbis) ->
                recent.add(path)
                addEnvNode(connection, dbis)
                setStatus("Opened ${connection.path} — ${dbis.size} database(s)")
            },
            onError = { t ->
                setStatus("Failed to open: ${t.message}")
                Messages.showErrorDialog(project, t.message ?: "Unknown error", "Open LMDB Environment")
            },
        )
    }

    private fun reloadRecentlyOpen() {
        // Re-attach environments that were left open in this IDE session.
        service.openConnections().values.forEach { connection ->
            runBg(
                work = { connection.listDatabases() },
                onSuccess = { dbis -> addEnvNode(connection, dbis) },
                onError = { },
            )
        }
    }

    /** Removes the tree node for [path], if present. Returns true if a node was removed. */
    private fun removeEnvNode(path: String): Boolean {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
            if ((child.userObject as? EnvNode)?.connection?.path == path) {
                rootNode.remove(i)
                return true
            }
        }
        return false
    }

    private fun addEnvNode(connection: LmdbConnection, dbis: List<DbiInfo>) {
        removeEnvNode(connection.path) // replace an existing node for the same path, if present
        val envNode = DefaultMutableTreeNode(EnvNode(connection))
        dbis.forEach { envNode.add(DefaultMutableTreeNode(DbiNode(connection, it))) }
        rootNode.add(envNode)
        treeModel.reload()
        tree.expandPath(javax.swing.tree.TreePath(arrayOf<Any>(rootNode, envNode)))
    }

    private fun selectDbi(dbiNode: DbiNode) {
        currentDbi = dbiNode
        nextKey = null
        tableModel.reset(emptyList())
        detailPanel.showEntry(null)
        loadPage(reset = true)
        showEnvStats(dbiNode.connection)
    }

    private fun applySearch() {
        if (currentDbi == null) return
        nextKey = null
        loadPage(reset = true)
    }

    private fun loadNextPage() = loadPage(reset = false)

    private fun loadPage(reset: Boolean) {
        val dbi = currentDbi ?: return
        if (loading) return
        loading = true
        loadMoreButton.isEnabled = false
        val prefix = parsePrefix(searchField.text)
        val after = if (reset) null else nextKey

        runBg(
            work = { dbi.connection.readPage(dbi.info.name, afterKey = after, prefix = prefix) },
            onSuccess = { page ->
                if (reset) tableModel.reset(page.entries) else tableModel.append(page.entries)
                nextKey = page.nextKey
                loadMoreButton.isEnabled = page.hasMore
                setStatus("${dbi.info.displayName}: showing ${tableModel.rowCount} entr${if (tableModel.rowCount == 1) "y" else "ies"}${if (page.hasMore) " (more available)" else ""}")
                loading = false
            },
            onError = { t ->
                loading = false
                setStatus("Read failed: ${t.message}")
            },
        )
    }

    private fun refreshSelected() {
        currentDbi?.let { selectDbi(it) }
    }

    private fun closeSelectedEnv() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val path = (node.userObject as? EnvNode)?.connection?.path
            ?: (node.userObject as? DbiNode)?.connection?.path
            ?: return
        service.close(path)
        removeEnvNode(path)
        treeModel.reload()
        tableModel.reset(emptyList())
        detailPanel.showEntry(null)
        currentDbi = null
        setStatus("Closed $path")
    }

    private fun showEnvStats(connection: LmdbConnection) {
        runBg(
            work = { connection.stats() },
            onSuccess = { s ->
                setStatus(
                    "map ${StringUtil.formatFileSize(s.mapSize)} · page ${s.pageSize} B · readers ${s.numReaders}/${s.maxReaders} · txn ${s.lastTransactionId} · ${s.dbiCount} DBIs"
                )
            },
            onError = { },
        )
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun parsePrefix(text: String): ByteArray? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return if (t.startsWith("0x", ignoreCase = true)) {
            val hex = t.substring(2).filter { !it.isWhitespace() }
            if (hex.length % 2 != 0) return null
            runCatching {
                ByteArray(hex.length / 2) { ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte() }
            }.getOrNull()
        } else {
            t.toByteArray(Charsets.UTF_8)
        }
    }

    private fun setStatus(text: String) {
        ApplicationManager.getApplication().invokeLater { statusLabel.text = text }
    }

    private fun <T> runBg(work: () -> T, onSuccess: (T) -> Unit, onError: (Throwable) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = work()
                ApplicationManager.getApplication().invokeLater { onSuccess(result) }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater { onError(t) }
            }
        }
    }

    /** Tree node payloads. */
    private class EnvNode(val connection: LmdbConnection) {
        override fun toString(): String = connection.path.substringAfterLast('/').substringAfterLast('\\')
            .ifEmpty { connection.path }
    }

    private class DbiNode(val connection: LmdbConnection, val info: DbiInfo) {
        override fun toString(): String {
            val count = if (info.entryCount >= 0) " (${info.entryCount})" else ""
            val dup = if (info.isDupSort) " [DUPSORT]" else ""
            return "${info.displayName}$count$dup"
        }
    }

    companion object {
        private val PANEL_KEY = Key.create<LmdbViewerPanel>("lmdbViewer.panel")

        fun register(project: Project, panel: LmdbViewerPanel) = project.putUserData(PANEL_KEY, panel)

        fun getInstance(project: Project): LmdbViewerPanel? = project.getUserData(PANEL_KEY)
    }
}
