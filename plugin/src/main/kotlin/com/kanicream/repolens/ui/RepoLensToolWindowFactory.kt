package com.kanicream.repolens.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Identity of the Repo Lens tool window and access to its content. */
internal object RepoLensToolWindow {

    /** Must match the `toolWindow` id registered in plugin.xml. */
    const val ID: String = "Repo Lens"

    fun panel(toolWindow: ToolWindow): RepoLensPanel? =
        toolWindow.contentManager.contents.firstNotNullOfOrNull { it.component as? RepoLensPanel }
}

/** Creates the Repo Lens tool window. DumbAware: Tier 0 analysis works while indexing. */
internal class RepoLensToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RepoLensPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
