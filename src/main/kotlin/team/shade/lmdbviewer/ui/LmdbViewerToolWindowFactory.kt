package team.shade.lmdbviewer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/** Builds the "LMDB Viewer" tool window content and registers the panel for actions to reach. */
class LmdbViewerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LmdbViewerPanel(project)
        LmdbViewerPanel.register(project, panel)

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}
