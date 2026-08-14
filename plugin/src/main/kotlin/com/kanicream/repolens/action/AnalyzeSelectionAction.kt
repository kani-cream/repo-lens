package com.kanicream.repolens.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.kanicream.repolens.ui.RepoLensToolWindow

/**
 * Analyzes the files selected in the Project View.
 *
 * The selection lives in the action's data context, which is the platform's way of
 * answering "what is the user pointing at"; the tool window cannot see it on its own.
 */
internal class AnalyzeSelectionAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && !e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).isNullOrEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList().orEmpty()
        if (files.isEmpty()) return

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RepoLensToolWindow.ID) ?: return
        toolWindow.activate {
            RepoLensToolWindow.panel(toolWindow)?.analyzeSelection(files)
        }
    }
}
