package team.shade.lmdbviewer.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
import team.shade.lmdbviewer.lmdb.LmdbEntry
import team.shade.lmdbviewer.lmdb.LmdbEnvironmentService
import team.shade.lmdbviewer.settings.RecentEnvironmentsService
import team.shade.lmdbviewer.transfer.EntryExporter
import team.shade.lmdbviewer.transfer.EntryImporter
import team.shade.lmdbviewer.transfer.TransferFormat
import team.shade.lmdbviewer.transfer.TransferRecord
import java.awt.BorderLayout
import java.io.File
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton
import javax.swing.KeyStroke
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
    private val importButton = JButton("Import…")
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
        registerShortcuts()
        reloadRecentlyOpen()
    }

    // ---- Keyboard shortcuts ---------------------------------------------------------------------

    /**
     * Binds keyboard shortcuts, active only while focus is inside this tool window. Ctrl-combinations
     * and F5 are safe on the whole panel; single keys (Insert/F2/Delete) are scoped to the table so
     * they don't fire while the user is typing in the search field.
     */
    private fun registerShortcuts() {
        shortcut("control F", this) { focusSearch() }
        shortcut("F5", this) { refreshSelected() }
        shortcut("control O", this) { chooseAndOpen() }
        shortcut("control W", this) { closeSelectedEnv() }
        shortcut("control E", this) { toggleEditModeFromShortcut() }
        shortcut("control shift DOWN", this) { if (loadMoreButton.isEnabled) loadNextPage() }
        shortcut("INSERT", table) { addEntry() }
        shortcut("F2", table) { editValue() }
        shortcut("DELETE", table) { deleteEntry() }
    }

    /** Registers [run] on [component] (and its descendants) for the given [keys] KeyStroke string. */
    private fun shortcut(keys: String, component: JComponent, run: () -> Unit) {
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = run()
        }.registerCustomShortcutSet(CustomShortcutSet.fromString(keys), component)
    }

    private fun focusSearch() {
        searchField.requestFocusInWindow()
        searchField.selectAll()
    }

    /** Flips the edit-mode toggle and runs the normal toggle flow (warning + reopen). */
    private fun toggleEditModeFromShortcut() {
        if (selectedConnection() == null) {
            setStatus("Select an environment first")
            return
        }
        editModeButton.isSelected = !editModeButton.isSelected
        onToggleEditMode()
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private fun buildToolbar(): JPanel = JPanel(BorderLayout()).apply {
        // Left-aligned environment/search controls.
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            add(JButton("Open Environment…").apply {
                toolTipText = "Open an LMDB environment — a directory, a data.mdb file, or a single-file .mdb store (Ctrl+O)."
                addActionListener { chooseAndOpen() }
            })
            add(JButton("Refresh").apply {
                toolTipText = "Reload the selected database from disk (F5)."
                addActionListener { refreshSelected() }
            })
            add(JButton("Close").apply {
                toolTipText = "Close the selected environment and remove it from the tree (Ctrl+W)."
                addActionListener { closeSelectedEnv() }
            })
            add(JBLabel("   Key prefix:"))
            searchField.columns = 18
            searchField.toolTipText = "Filter by key prefix. Plain text = UTF-8; prefix with 0x for hex (e.g. 0x00ff). Focus with Ctrl+F."
            searchField.addActionListener { applySearch() }
            add(searchField)
            add(JButton("Find").apply {
                toolTipText = "Apply the key-prefix filter (Enter)."
                addActionListener { applySearch() }
            })
        }
        // Read/Edit mode toggle, pinned to the right edge.
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            editModeButton.toolTipText = "Toggle read-only / edit mode for the selected environment (Ctrl+E)."
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
        addButton.apply { toolTipText = "Add a new key/value entry (Insert)."; addActionListener { addEntry() } }
        editButton.apply { toolTipText = "Edit the value of the selected entry (F2)."; addActionListener { editValue() } }
        deleteButton.apply { toolTipText = "Delete the selected entry (Delete)."; addActionListener { deleteEntry() } }
        importButton.apply {
            toolTipText = "Import entries from a JSON/NDJSON file into the selected database (edit mode)."
            addActionListener { importIntoCurrentDbi() }
        }
        add(addButton); add(editButton); add(deleteButton); add(importButton)

        loadMoreButton.isEnabled = false
        loadMoreButton.toolTipText = "Load the next page of entries (Ctrl+Shift+Down)."
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
        configureTreePopup()
    }

    /** Right-click menu on the tree: export a DBI or the whole environment, and import into a DBI. */
    private fun configureTreePopup() {
        val exportDbiItem = JMenuItem("Export DBI…").apply {
            toolTipText = "Export the selected database to a JSON, NDJSON, or CSV file."
            addActionListener { exportSelectedDbi() }
        }
        val exportEnvItem = JMenuItem("Export environment…").apply {
            toolTipText = "Export every database in the environment to one file."
            addActionListener { exportSelectedEnv() }
        }
        val importItem = JMenuItem("Import into DBI…").apply {
            toolTipText = "Import entries from a JSON/NDJSON file into the selected database (edit mode)."
            addActionListener { importIntoSelectedDbi() }
        }
        val popup = JPopupMenu().apply {
            add(exportDbiItem)
            add(exportEnvItem)
            addSeparator()
            add(importItem)
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                tree.selectionPath = path
                val obj = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject
                val dbi = obj as? DbiNode
                val conn = (obj as? EnvNode)?.connection ?: dbi?.connection
                exportDbiItem.isEnabled = dbi != null
                exportEnvItem.isEnabled = conn != null
                importItem.isEnabled = dbi != null && dbi.connection.writable
                popup.show(tree, e.x, e.y)
            }
        })
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
            editPopupItem.apply {
                toolTipText = "Edit the value of the selected entry (F2)."
                accelerator = KeyStroke.getKeyStroke("F2")
                addActionListener { editValue() }
            }
            deletePopupItem.apply {
                toolTipText = "Delete the selected entry (Delete)."
                accelerator = KeyStroke.getKeyStroke("DELETE")
                addActionListener { deleteEntry() }
            }
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

    // ---- Export / import ------------------------------------------------------------------------

    private fun selectedNodeObject(): Any? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject

    private fun exportSelectedDbi() {
        val dbi = selectedNodeObject() as? DbiNode ?: currentDbi
        if (dbi == null) { setStatus("Select a database to export"); return }
        exportDbi(dbi.connection, dbi.info.name, dbi.info.displayName)
    }

    private fun exportSelectedEnv() {
        val obj = selectedNodeObject()
        val conn = (obj as? EnvNode)?.connection ?: (obj as? DbiNode)?.connection ?: currentDbi?.connection
        if (conn == null) { setStatus("Select an environment to export"); return }
        exportEnv(conn)
    }

    private fun importIntoSelectedDbi() {
        val dbi = selectedNodeObject() as? DbiNode
        if (dbi == null) { setStatus("Select a database to import into"); return }
        doImport(dbi.connection, dbi.info.name, dbi.info.displayName)
    }

    private fun importIntoCurrentDbi() {
        val dbi = currentDbi
        if (dbi == null) { setStatus("Select a database to import into"); return }
        doImport(dbi.connection, dbi.info.name, dbi.info.displayName)
    }

    /** Streams every entry of one DBI to a file in the chosen format. */
    private fun exportDbi(conn: LmdbConnection, dbiName: String?, displayName: String) {
        val format = chooseFormat("Export DBI") ?: return
        val file = chooseSaveFile("Export DBI — $displayName", "${fileBase(displayName)}.${format.extension}") ?: return
        setStatus("Exporting $displayName…")
        runBg(
            work = {
                var n = 0L
                file.bufferedWriter(Charsets.UTF_8).use { w ->
                    EntryExporter(w, format, includeDb = false).use { exp ->
                        conn.forEachEntry(dbiName) { e -> exp.write(TransferRecord(null, e.key, e.value)); n++ }
                    }
                }
                n
            },
            onSuccess = { n -> exportDone(n, file) },
            onError = { t -> transferError("export", t) },
        )
    }

    /** Streams every DBI of an environment to one file, tagging each record with its DBI name. */
    private fun exportEnv(conn: LmdbConnection) {
        val format = chooseFormat("Export Environment") ?: return
        val file = chooseSaveFile("Export Environment", "${fileBase(envBaseName(conn))}.${format.extension}") ?: return
        setStatus("Exporting environment…")
        runBg(
            work = {
                var n = 0L
                val dbis = conn.listDatabases()
                file.bufferedWriter(Charsets.UTF_8).use { w ->
                    EntryExporter(w, format, includeDb = true).use { exp ->
                        dbis.forEach { info ->
                            conn.forEachEntry(info.name) { e -> exp.write(TransferRecord(info.name, e.key, e.value)); n++ }
                        }
                    }
                }
                n
            },
            onSuccess = { n -> exportDone(n, file) },
            onError = { t -> transferError("export", t) },
        )
    }

    /** Reads a JSON/NDJSON file and writes its records into [dbiName] in batches (edit mode only). */
    private fun doImport(conn: LmdbConnection, dbiName: String?, displayName: String) {
        if (!conn.writable) { setStatus("Enable edit mode before importing"); return }
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Import into $displayName")
            .withDescription("Select a JSON or NDJSON file exported from LMDB Viewer")
        val vf = FileChooser.chooseFile(descriptor, project, null) ?: return
        val format = TransferFormat.fromFileName(vf.name)?.takeIf { it.importable }
            ?: chooseFormat("Import", importOnly = true) ?: return
        val choice = Messages.showYesNoDialog(
            project,
            "Import records from ${vf.name} into $displayName?\n\nExisting keys are overwritten. This cannot be undone.",
            "Import Into DBI",
            "Import",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (choice != Messages.YES) return
        setStatus("Importing into $displayName…")
        runBg(
            work = {
                var n = 0L
                File(vf.path).bufferedReader(Charsets.UTF_8).use { r ->
                    EntryImporter.read(r, format).chunked(IMPORT_BATCH).forEach { batch ->
                        conn.mutations.putBatch(dbiName, batch.map { LmdbEntry(it.key, it.value) })
                        n += batch.size
                    }
                }
                n
            },
            onSuccess = { n ->
                setStatus("Imported $n entr${plural(n)} into $displayName")
                notifyInfo("Import complete", "$n entr${plural(n)} imported into $displayName")
                refreshAfterMutation(conn, dbiName)
            },
            onError = { t -> transferError("import", t) },
        )
    }

    private fun exportDone(n: Long, file: File) {
        setStatus("Exported $n entr${plural(n)} to ${file.name}")
        notifyInfo("Export complete", "$n entr${plural(n)} written to ${file.path}")
    }

    private fun chooseFormat(title: String, importOnly: Boolean = false): TransferFormat? {
        val formats = TransferFormat.values().filter { !importOnly || it.importable }
        val names = formats.map { it.name }.toTypedArray()
        val idx = Messages.showChooseDialog(project, "Choose a format:", title, null, names, names.first())
        return formats.getOrNull(idx)
    }

    private fun chooseSaveFile(title: String, defaultName: String): File? {
        val descriptor = FileSaverDescriptor(title, "Choose where to save the export")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        return dialog.save(null as VirtualFile?, defaultName)?.file
    }

    private fun notifyInfo(title: String, message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LMDB Viewer")
                .createNotification(title, message, NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun transferError(op: String, t: Throwable) {
        setStatus("Failed to $op: ${t.message}")
        Messages.showErrorDialog(
            project,
            t.message ?: "Unknown error",
            "LMDB ${op.replaceFirstChar { it.uppercase() }} Failed",
        )
    }

    private fun fileBase(name: String): String =
        name.ifBlank { "export" }.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun envBaseName(conn: LmdbConnection): String =
        conn.path.substringAfterLast('/').substringAfterLast('\\').ifEmpty { "environment" }

    private fun plural(n: Long): String = if (n == 1L) "y" else "ies"

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

    /** Reflects the current mode on the toggle via coloured text: green "Read mode", red "Edit mode". */
    private fun updateEditModeButtonAppearance() {
        val editing = editModeButton.isSelected
        val label = if (editing) "Edit mode" else "Read mode"
        val hex = if (editing) EDIT_MODE_HEX else READ_MODE_HEX
        // Colour the label (not the button fill); the colour is baked into HTML so the L&F can't
        // override it (plain setForeground was being ignored for the selected toggle state).
        editModeButton.text = "<html><b><font color=\"#$hex\">$label</font></b></html>"
    }

    private fun updateEditActions() {
        val envConn = selectedConnection()
        editModeButton.isEnabled = envConn != null
        setToggleSelectedSilently(envConn?.writable == true)

        val writable = currentDbi?.connection?.writable == true
        val hasRow = table.selectedRow >= 0
        addButton.isEnabled = writable
        importButton.isEnabled = writable
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

        // Mode-toggle label colours (green = safe read-only, red = writing enabled).
        private const val READ_MODE_HEX = "59A869" // green — read-only (default)
        private const val EDIT_MODE_HEX = "DB5860" // red — editing enabled

        // Entries written per write txn during import (one commit per batch).
        private const val IMPORT_BATCH = 1000

        fun register(project: Project, panel: LmdbViewerPanel) = project.putUserData(PANEL_KEY, panel)

        fun getInstance(project: Project): LmdbViewerPanel? = project.getUserData(PANEL_KEY)
    }
}
