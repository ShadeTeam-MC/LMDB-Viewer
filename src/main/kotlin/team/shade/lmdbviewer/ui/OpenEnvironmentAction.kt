package team.shade.lmdbviewer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

/**
 * `File ▸ Open LMDB Environment…` — activates the LMDB Viewer tool window and shows the open dialog.
 * Also reachable from the Project view "Open in LMDB Viewer" path once a file is selected.
 */
class OpenEnvironmentAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("LMDB Viewer") ?: return
        toolWindow.activate {
            val panel = LmdbViewerPanel.getInstance(project) ?: return@activate
            // If a concrete *.mdb file was selected in the Project view, open it directly.
            val selected = e.getData(CommonDataKeys.VIRTUAL_FILE)
            val path = selected?.path
            if (path != null && (selected.name.endsWith(".mdb") || selected.isDirectory)) {
                panel.openEnvironment(path)
            } else {
                panel.chooseAndOpen()
            }
        }
    }
}
