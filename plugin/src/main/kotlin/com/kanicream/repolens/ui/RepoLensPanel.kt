package com.kanicream.repolens.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.clipboard.CopyForAiService
import com.kanicream.repolens.format.MetricFormat
import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.navigation.FindingNavigator
import com.kanicream.repolens.services.AnalysisListener
import com.kanicream.repolens.services.RepoLensAnalysisService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

/**
 * Repo Lens tool window content: scope selector + Analyze/Stop, findings table, detail
 * pane, and Copy for AI. This class only handles user interaction and rendering; all
 * analysis, navigation, and clipboard work is delegated to the application services.
 */
internal class RepoLensPanel(private val project: Project) : JPanel(BorderLayout()), AnalysisListener {

    private val tableModel = FindingTableModel()
    private val table = JBTable(tableModel)
    private val detailArea = JBTextArea()
    private val statusLabel = JBLabel("Ready")
    private val scopeCombo = ComboBox(AnalysisScopeType.entries.toTypedArray())
    private val analyzeButton = JButton("Analyze")
    private val stopButton = JButton("Stop")
    private val copyForAiButton = JButton("Copy for AI")

    init {
        buildUi()
        wireActions()
    }

    private fun buildUi() {
        scopeCombo.renderer = textListCellRenderer { it.displayName }

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.emptyText.setText("Run Analyze to collect review candidates")

        detailArea.isEditable = false
        detailArea.lineWrap = true
        detailArea.wrapStyleWord = true
        detailArea.margin = JBUI.insets(6)

        stopButton.isEnabled = false
        copyForAiButton.isEnabled = false

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            add(JBLabel("Scope:"))
            add(scopeCombo)
            add(analyzeButton)
            add(stopButton)
            add(copyForAiButton)
            add(statusLabel)
        }

        val splitter = OnePixelSplitter(true, 0.7f).apply {
            firstComponent = JBScrollPane(table)
            secondComponent = JBScrollPane(detailArea)
        }

        add(toolbar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
    }

    private fun wireActions() {
        analyzeButton.addActionListener { startAnalysis() }
        stopButton.addActionListener { RepoLensAnalysisService.getInstance(project).stop() }
        copyForAiButton.addActionListener { copySelectionForAi() }

        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) onSelectionChanged()
        }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelection()
            }
        })
        table.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), NAVIGATE_ACTION_KEY)
        table.actionMap.put(
            NAVIGATE_ACTION_KEY,
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = navigateToSelection()
            },
        )
    }

    private fun startAnalysis() {
        setRunning(true)
        statusLabel.text = "Analyzing…"
        RepoLensAnalysisService.getInstance(project).startProjectAnalysis(this)
    }

    override fun onFinished(result: AnalysisResult) {
        tableModel.setFindings(result.findings)
        setRunning(false)
        val counts = "Total ${result.findings.size} | " +
            "Warning ${result.countBySeverity(Severity.WARNING)} | " +
            "Info ${result.countBySeverity(Severity.INFO)}"
        statusLabel.text = if (result.failures.isEmpty()) {
            counts
        } else {
            "$counts | ${result.failures.size} analyzer(s) failed"
        }
        detailArea.text = ""
    }

    override fun onCancelled() {
        setRunning(false)
        statusLabel.text = "Analysis cancelled"
    }

    override fun onFailed(error: Throwable) {
        setRunning(false)
        statusLabel.text = "Analysis failed: ${error.javaClass.simpleName}"
    }

    private fun setRunning(running: Boolean) {
        analyzeButton.isEnabled = !running
        scopeCombo.isEnabled = !running
        stopButton.isEnabled = running
    }

    private fun onSelectionChanged() {
        val selected = selectedFindings()
        copyForAiButton.isEnabled = selected.isNotEmpty()
        detailArea.text = when (selected.size) {
            0 -> ""
            1 -> detailText(selected.single())
            else -> "${selected.size} findings selected"
        }
        detailArea.caretPosition = 0
    }

    private fun navigateToSelection() {
        val finding = selectedFindings().firstOrNull() ?: return
        if (!FindingNavigator.getInstance(project).navigate(finding)) {
            statusLabel.text = "Cannot navigate: ${finding.location.filePath} not found"
        }
    }

    private fun copySelectionForAi() {
        val selected = selectedFindings()
        if (selected.isEmpty()) return
        val scopeName = (scopeCombo.selectedItem as? AnalysisScopeType)?.displayName
            ?: AnalysisScopeType.PROJECT.displayName
        CopyForAiService.getInstance(project).copyForAi(selected, scopeName) {
            statusLabel.text = "Copied ${selected.size} finding(s) as Markdown"
        }
    }

    private fun selectedFindings(): List<Finding> =
        table.selectedRows
            .map(table::convertRowIndexToModel)
            .mapNotNull(tableModel::findingAt)

    private fun detailText(finding: Finding): String = buildString {
        appendLine("${finding.checkName} (${finding.severity.displayName})")
        appendLine("File: ${finding.location.filePath}")
        finding.symbol?.let { appendLine("Symbol: ${it.displayName}") }
        appendLine("Location: ${finding.location.lineRangeText}")
        finding.measuredValue?.let { value ->
            val threshold = finding.threshold
                ?.let { " (threshold ${MetricFormat.format(it)})" }
                .orEmpty()
            appendLine("Value: ${MetricFormat.format(value)}$threshold")
        }
        appendLine()
        append(finding.message)
        finding.metadata[TodoMarkerAnalyzer.METADATA_TEXT]?.let { text ->
            appendLine()
            appendLine()
            append(text)
        }
    }

    companion object {
        private const val NAVIGATE_ACTION_KEY = "repoLens.navigateToFinding"
    }
}
