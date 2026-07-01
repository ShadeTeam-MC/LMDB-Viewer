package team.shade.lmdbviewer.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
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

    // Edit-mode controls (writable access is opt-in, per environment).
    // The toggle shows the *current* mode: "Read mode" (green) when read-only, "Edit mode" (red) on.
    private val editModeButton = JToggleButton("Read mode")
    private val addButton = JButton("Add…")
    private val editButton = JButton("Edit")
    private val deleteButton = JButton("Delete")
    private val editPopupItem = JMenuItem("Edit value…")
    private val deletePopupItem = JMenuItem("Delete")
    private var updatingToggle = false

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

    private fun buildToolbar(): JPanel = JPanel(BorderLayout()).apply {
        // Left-aligned environment/search controls.
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
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
        // Read/Edit mode toggle, pinned to the right edge.
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            editModeButton.toolTipText = "Toggle read-only / edit mode for the selected environment."
            editModeButton.addActionListener { onToggleEditMode() }
            add(editModeButton)
        }
        add(left, BorderLayout.WEST)
        add(right, BorderLayout.EAST)
        updateEditModeButtonAppearance()
    }

    private fun buildBody(): OnePixelSplitter {
        val treeScroll = JBScrollPane(tree)

        val tablePanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(buildTableActionsBar(), BorderLayout.SOUTH)
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

    /** The row of entry actions directly under the entries table: Add / Edit / Delete + Load more. */
    private fun buildTableActionsBar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
        addButton.apply { toolTipText = "Add a new key/value entry."; addActionListener { addEntry() } }
        editButton.apply { toolTipText = "Edit the value of the selected entry."; addActionListener { editValue() } }
        deleteButton.apply { toolTipText = "Delete the selected entry."; addActionListener { deleteEntry() } }
        add(addButton); add(editButton); add(deleteButton)

        loadMoreButton.isEnabled = false
        loadMoreButton.addActionListener { loadNextPage() }
        add(loadMoreButton)
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
            val dbi = node.userObject as? DbiNode
            if (dbi != null) selectDbi(dbi) else updateEditActions()
        }
    }

    private fun configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.columnModel.getColumn(2).maxWidth = 90
        table.selectionModel.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            val entry = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.entryAt(it) }
            detailPanel.showEntry(entry)
            updateEditActions()
        }

        val popup = JPopupMenu().apply {
            editPopupItem.addActionListener { editValue() }
            deletePopupItem.addActionListener { deleteEntry() }
            add(editPopupItem)
            add(deletePopupItem)
        }
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point)
                if (row >= 0) table.setRowSelectionInterval(row, row)
                updateEditActions()
                popup.show(table, e.x, e.y)
            }
        })
        updateEditActions()
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
        // Warn when a write grows this environment's map size (writable envs only).
        connection.onMapResized = { newSize -> notifyMapResized(newSize) }
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
        updateEditActions()
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

    // ---- Editing --------------------------------------------------------------------------------

    /** The environment the edit controls act on: the selected DBI's env, else the selected tree node's. */
    private fun selectedConnection(): LmdbConnection? {
        currentDbi?.let { return it.connection }
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return (node.userObject as? EnvNode)?.connection ?: (node.userObject as? DbiNode)?.connection
    }

    private fun onToggleEditMode() {
        if (updatingToggle) return
        val conn = selectedConnection()
        if (conn == null) {
            setToggleSelectedSilently(false)
            setStatus("Select an environment first")
            return
        }
        val wantWritable = editModeButton.isSelected
        if (conn.writable == wantWritable) return
        if (wantWritable) {
            val choice = Messages.showYesNoDialog(
                project,
                "Edit mode reopens this environment for WRITING and removes read-only safety.\n\n" +
                    "Make sure no other process has it open. Changes are written directly to the database " +
                    "and cannot be undone.\n\nContinue?",
                "Enable Edit Mode",
                "Enable",
                "Cancel",
                Messages.getWarningIcon(),
            )
            if (choice != Messages.YES) {
                setToggleSelectedSilently(false)
                return
            }
        }
        reopen(conn.path, wantWritable)
    }

    /** Closes and reopens [path] in the requested mode, then rebuilds its node and reselects the DBI. */
    private fun reopen(path: String, writable: Boolean) {
        val keepDbi = currentDbi?.info?.name
        setStatus(if (writable) "Reopening for writing…" else "Reopening read-only…")
        runBg(
            work = {
                val connection = service.open(path, writable)
                connection to connection.listDatabases()
            },
            onSuccess = { (connection, dbis) ->
                addEnvNode(connection, dbis)
                // Drop the reference to the now-closed old connection; reselect re-sets it on success.
                currentDbi = null
                tableModel.reset(emptyList())
                detailPanel.showEntry(null)
                reselectDbi(connection, keepDbi)
                updateEditActions()
                setStatus("${if (writable) "Edit mode ON" else "Read-only"} — ${connection.path}")
            },
            onError = { t ->
                setToggleSelectedSilently(false)
                updateEditActions()
                setStatus("Failed to reopen: ${t.message}")
                Messages.showErrorDialog(project, t.message ?: "Unknown error", "Reopen Environment")
            },
        )
    }

    private fun addEntry() {
        val dbi = currentDbi ?: return
        val conn = dbi.connection
        if (!conn.writable) return
        val dialog = EntryEditorDialog.forAdd(project)
        if (!dialog.showAndGet()) return
        val key = dialog.resultKey ?: return
        val value = dialog.resultValue ?: return
        runBg(
            work = { conn.mutations.put(dbi.info.name, key, value) },
            onSuccess = { setStatus("Added entry"); refreshAfterMutation(conn, dbi.info.name) },
            onError = { t -> mutationError("add", t) },
        )
    }

    private fun editValue() {
        val dbi = currentDbi ?: return
        val conn = dbi.connection
        if (!conn.writable) return
        val entry = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.entryAt(it) } ?: return
        val dialog = EntryEditorDialog.forEditValue(project, entry.key, entry.value)
        if (!dialog.showAndGet()) return
        val value = dialog.resultValue ?: return
        runBg(
            work = { conn.mutations.put(dbi.info.name, entry.key, value) },
            onSuccess = { setStatus("Updated value"); refreshAfterMutation(conn, dbi.info.name) },
            onError = { t -> mutationError("update", t) },
        )
    }

    private fun deleteEntry() {
        val dbi = currentDbi ?: return
        val conn = dbi.connection
        if (!conn.writable) return
        val entry = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.entryAt(it) } ?: return
        val choice = Messages.showYesNoDialog(
            project,
            "Delete entry with key:\n${Previews.preview(entry.key)}\n\nThis cannot be undone.",
            "Delete Entry",
            "Delete",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (choice != Messages.YES) return
        runBg(
            work = { conn.mutations.delete(dbi.info.name, entry.key, entry.value) },
            onSuccess = { setStatus("Deleted entry"); refreshAfterMutation(conn, dbi.info.name) },
            onError = { t -> mutationError("delete", t) },
        )
    }

    /** After a write: re-list DBIs (refreshes entry counts) and reload the current DBI's page. */
    private fun refreshAfterMutation(connection: LmdbConnection, dbiName: String?) {
        runBg(
            work = { connection.listDatabases() },
            onSuccess = { dbis ->
                addEnvNode(connection, dbis)
                reselectDbi(connection, dbiName)
            },
            onError = { },
        )
    }

    private fun mutationError(op: String, t: Throwable) {
        setStatus("Failed to $op entry: ${t.message}")
        Messages.showErrorDialog(
            project,
            t.message ?: "Unknown error",
            "LMDB ${op.replaceFirstChar { it.uppercase() }} Failed",
        )
    }

    /** Warns that a write outgrew the map and it was enlarged. Invoked off the EDT from the write. */
    private fun notifyMapResized(newSize: Long) {
        val size = StringUtil.formatFileSize(newSize)
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LMDB Viewer")
                .createNotification(
                    "LMDB map size expanded",
                    "A write ran out of mapped space, so the environment was enlarged to $size.",
                    NotificationType.WARNING,
                )
                .notify(project)
            setStatus("Map size expanded to $size")
        }
    }

    private fun findEnvNode(path: String): DefaultMutableTreeNode? {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
            if ((child.userObject as? EnvNode)?.connection?.path == path) return child
        }
        return null
    }

    /** Selects the DBI named [dbiName] under [connection]'s node, firing the normal selection flow. */
    private fun reselectDbi(connection: LmdbConnection, dbiName: String?) {
        val envNode = findEnvNode(connection.path) ?: return
        for (i in 0 until envNode.childCount) {
            val child = envNode.getChildAt(i) as DefaultMutableTreeNode
            val dbi = child.userObject as? DbiNode ?: continue
            if (dbi.info.name == dbiName) {
                tree.selectionPath = TreePath(arrayOf<Any>(rootNode, envNode, child))
                return
            }
        }
    }

    private fun setToggleSelectedSilently(selected: Boolean) {
        updatingToggle = true
        editModeButton.isSelected = selected
        updatingToggle = false
        updateEditModeButtonAppearance()
    }

    /** Reflects the current mode on the toggle: green "Read mode" when off, red "Edit mode" when on. */
    private fun updateEditModeButtonAppearance() {
        val editing = editModeButton.isSelected
        val label = if (editing) "Edit mode" else "Read mode"
        // Force the text colour via HTML so it stays white regardless of how the L&F resolves the
        // (selected) foreground — that resolution is what kept making the label blend into the fill.
        editModeButton.text = "<html><b><font color=\"#FFFFFF\">$label</font></b></html>"
        editModeButton.isOpaque = true
        editModeButton.isFocusPainted = false
        editModeButton.background = if (editing) EDIT_MODE_COLOR else READ_MODE_COLOR
        // FlatLaf (IntelliJ's L&F) paints its own button background; set the fill for every state,
        // including the selected/pressed states of the toggle.
        val hex = if (editing) "DB5860" else "59A869"
        editModeButton.putClientProperty(
            "FlatLaf.style",
            "background: #$hex; selectedBackground: #$hex; pressedBackground: #$hex; hoverBackground: #$hex",
        )
    }

    private fun updateEditActions() {
        val envConn = selectedConnection()
        editModeButton.isEnabled = envConn != null
        setToggleSelectedSilently(envConn?.writable == true)

        val writable = currentDbi?.connection?.writable == true
        val hasRow = table.selectedRow >= 0
        addButton.isEnabled = writable
        editButton.isEnabled = writable && hasRow
        deleteButton.isEnabled = writable && hasRow
        editPopupItem.isEnabled = writable && hasRow
        deletePopupItem.isEnabled = writable && hasRow
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
        updateEditActions()
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
        override fun toString(): String {
            val name = connection.path.substringAfterLast('/').substringAfterLast('\\')
                .ifEmpty { connection.path }
            return if (connection.writable) "$name  [RW]" else name
        }
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

        // Mode-toggle colours (same in light and dark themes for a clear green=safe / red=writing cue).
        private val READ_MODE_COLOR = JBColor(0x59A869, 0x59A869) // green — read-only (default)
        private val EDIT_MODE_COLOR = JBColor(0xDB5860, 0xDB5860) // red — editing enabled

        fun register(project: Project, panel: LmdbViewerPanel) = project.putUserData(PANEL_KEY, panel)

        fun getInstance(project: Project): LmdbViewerPanel? = project.getUserData(PANEL_KEY)
    }
}
