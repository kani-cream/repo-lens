package com.kanicream.repolens.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Creates the Repo Lens tool window. DumbAware: Tier 0 analysis works while indexing. */
internal class RepoLensToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(RepoLensPanel(project), "", false)
        toolWindow.contentManager.addContent(content)
    }
}
