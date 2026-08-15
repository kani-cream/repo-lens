package com.kanicream.repolens.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.kanicream.repolens.analysis.structure.CircularDependencyAnalyzer
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.clipboard.CopyStyle
import com.kanicream.repolens.clipboard.FindingCopyService
import com.kanicream.repolens.filter.FindingFilter
import com.kanicream.repolens.format.MetricFormat
import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.navigation.FindingNavigator
import com.kanicream.repolens.services.RepoLensAnalysisListener
import com.kanicream.repolens.services.RepoLensAnalysisService
import com.kanicream.repolens.settings.RepoLensSettings
import com.kanicream.repolens.suppression.SuppressionKind
import com.kanicream.repolens.suppression.SuppressionPolicy
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Repo Lens tool window content: scope selector + Analyze/Stop, findings table, detail
 * pane, and Copy for AI. This class only handles user interaction and rendering; all
 * analysis, navigation, and clipboard work is delegated to the application services.
 */
internal class RepoLensPanel(private val project: Project) :
    JPanel(BorderLayout()), RepoLensAnalysisListener, Disposable {

    private val tableModel = FindingTableModel()
    private val table = JBTable(tableModel)
    private val detailArea = JBTextArea()
    private val statusLabel = JBLabel("Ready")
    private val scopeCombo = ComboBox(AnalysisScopeType.entries.toTypedArray())
    private val searchField = SearchTextField()
    private val severityCombo = ComboBox(SEVERITY_CHOICES.toTypedArray())
    private val checkCombo = ComboBox(arrayOf(ALL_CHECKS))
    private val showHiddenCheckBox = JBCheckBox("Show hidden")
    private val analyzeButton = JButton("Analyze")
    private val stopButton = JButton("Stop")
    private val copyButtons: Map<CopyStyle, JButton> =
        CopyStyle.entries.associateWith { JButton(it.actionName) }

    /** Selection captured by the Project View action, re-used when Analyze is pressed again. */
    private var selectedFiles: List<VirtualFile> = emptyList()

    /** Unfiltered result of the last run; filters never trigger a re-analysis. */
    private var allFindings: List<Finding> = emptyList()

    /** Guards against filtering while the Check combo is being rebuilt. */
    private var rebuildingFilters = false

    init {
        buildUi()
        wireActions()
        project.messageBus.connect(this).subscribe(RepoLensAnalysisListener.TOPIC, this)
    }

    override fun dispose() = Unit

    private fun buildUi() {
        scopeCombo.renderer = textListCellRenderer { it.displayName }

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.emptyText.setText("Run Analyze to collect review candidates")

        detailArea.isEditable = false
        detailArea.lineWrap = true
        detailArea.wrapStyleWord = true
        detailArea.margin = JBUI.insets(6)

        stopButton.isEnabled = false
        copyButtons.values.forEach { it.isEnabled = false }

        searchField.textEditor.emptyText.setText("Search findings")
        severityCombo.renderer = textListCellRenderer { it.label }
        checkCombo.renderer = textListCellRenderer { it }

        val actionRow = row().apply {
            add(JBLabel("Scope:"))
            add(scopeCombo)
            add(analyzeButton)
            add(stopButton)
            copyButtons.values.forEach(::add)
        }
        val filterRow = row().apply {
            add(searchField)
            add(JBLabel("Severity:"))
            add(severityCombo)
            add(JBLabel("Check:"))
            add(checkCombo)
            add(showHiddenCheckBox)
            add(statusLabel)
        }
        val toolbar = JPanel(GridLayout(2, 1)).apply {
            add(actionRow)
            add(filterRow)
        }

        val splitter = OnePixelSplitter(true, 0.7f).apply {
            firstComponent = JBScrollPane(table)
            secondComponent = JBScrollPane(detailArea)
        }

        add(toolbar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
    }

    private fun row(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2)))

    private fun wireActions() {
        analyzeButton.addActionListener { startAnalysis() }
        stopButton.addActionListener { RepoLensAnalysisService.getInstance(project).stop() }
        copyButtons.forEach { (style, button) ->
            button.addActionListener { copySelection(style) }
        }

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = applyFilters()
        })
        severityCombo.addActionListener { applyFilters() }
        checkCombo.addActionListener { applyFilters() }
        showHiddenCheckBox.addActionListener { applyFilters() }
        table.componentPopupMenu = buildTablePopup()

        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) onSelectionChanged()
        }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelection()
            }

            override fun mousePressed(e: MouseEvent) = selectRowForPopup(e)

            override fun mouseReleased(e: MouseEvent) = selectRowForPopup(e)

            /** Right-clicking a row outside the selection targets that row, as everywhere else in the IDE. */
            private fun selectRowForPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point)
                if (row >= 0 && row !in table.selectedRows) {
                    table.setRowSelectionInterval(row, row)
                }
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
        RepoLensAnalysisService.getInstance(project).startAnalysis(selectedScope(), selectedFiles)
    }

    /** Runs an analysis for files picked in the Project View. */
    fun analyzeSelection(files: List<VirtualFile>) {
        selectedFiles = files
        scopeCombo.selectedItem = AnalysisScopeType.SELECTED_FILES
        RepoLensAnalysisService.getInstance(project).startAnalysis(AnalysisScopeType.SELECTED_FILES, files)
    }

    override fun analysisStarted(scopeType: AnalysisScopeType) {
        scopeCombo.selectedItem = scopeType
        setRunning(true)
        statusLabel.text = "Analyzing ${scopeType.displayName.lowercase()}…"
    }

    override fun analysisFinished(scopeType: AnalysisScopeType, result: AnalysisResult) {
        allFindings = result.findings
        failedAnalyzerCount = result.failures.size
        skippedAnalyzers = result.skips
        setRunning(false)
        repopulateCheckFilter()
        applyFilters()
        detailArea.text = ""
    }

    /** Keeps the Check filter in step with the checks that actually produced findings. */
    private fun repopulateCheckFilter() {
        val previous = checkCombo.selectedItem as? String
        val checks = allFindings.map { it.checkName }.distinct().sorted()
        rebuildingFilters = true
        try {
            checkCombo.removeAllItems()
            checkCombo.addItem(ALL_CHECKS)
            checks.forEach(checkCombo::addItem)
            checkCombo.selectedItem = if (previous != null && previous in checks) previous else ALL_CHECKS
        } finally {
            rebuildingFilters = false
        }
    }

    private fun currentFilter(): FindingFilter {
        val severity = (severityCombo.selectedItem as? SeverityChoice)?.severity
        val check = (checkCombo.selectedItem as? String)?.takeUnless { it == ALL_CHECKS }
        return FindingFilter(
            searchText = searchField.text,
            severities = setOfNotNull(severity),
            checkNames = setOfNotNull(check),
        )
    }

    private fun applyFilters() {
        if (rebuildingFilters) return
        val policy = suppressionPolicy()
        val (active, suppressed) = policy.partition(allFindings)
        val base = if (showHiddenCheckBox.isSelected) allFindings else active
        val filter = currentFilter()
        val visible = filter.apply(base)
        tableModel.setFindings(visible)
        val ignoredCount = suppressed.count { policy.suppressionOf(it) == SuppressionKind.IGNORED }
        statusLabel.text =
            statusText(filter, visible, base.size, ignoredCount, suppressed.size - ignoredCount)
        statusLabel.toolTipText = skippedAnalyzers
            .joinToString("<br>") { "${it.analyzerId}: ${it.reason}" }
            .ifEmpty { null }
        onSelectionChanged()
    }

    private fun suppressionPolicy(): SuppressionPolicy =
        RepoLensSettings.getInstance(project).suppressionPolicy()

    private fun statusText(
        filter: FindingFilter,
        visible: List<Finding>,
        baseCount: Int,
        ignoredCount: Int,
        ruleSuppressedCount: Int,
    ): String {
        val shown = if (filter.isActive && visible.size != baseCount) {
            "Showing ${visible.size} of $baseCount"
        } else {
            "Total $baseCount"
        }
        val counts = "$shown | Warning ${visible.count { it.severity == Severity.WARNING }} | " +
            "Info ${visible.count { it.severity == Severity.INFO }}"
        // Manual ignores and rule hits are different tools; lumping them into one number
        // reads as "why are there 34?" the first time a broad rule matches.
        val hiddenParts = buildList {
            if (ignoredCount > 0) add("$ignoredCount ignored")
            if (ruleSuppressedCount > 0) add("$ruleSuppressedCount by rules")
        }
        val withHidden = if (hiddenParts.isEmpty()) {
            counts
        } else {
            "$counts | ${(ignoredCount + ruleSuppressedCount)} hidden (${hiddenParts.joinToString(", ")})"
        }
        val withSkips = if (skippedAnalyzers.isEmpty()) {
            withHidden
        } else {
            // e.g. "1 check skipped (indexing)" - the reason itself is in the log and
            // in the tooltip via the detail text of any run; keep the status short.
            "$withHidden | ${skippedAnalyzers.size} check(s) skipped"
        }
        return if (failedAnalyzerCount == 0) {
            withSkips
        } else {
            "$withSkips | $failedAnalyzerCount analyzer(s) failed"
        }
    }

    /** Ignore / unignore for the selected findings; suppressed state is view-side only. */
    private fun buildTablePopup(): JPopupMenu {
        val menu = JPopupMenu()
        val ignoreItem = JMenuItem()
        ignoreItem.addActionListener {
            val settings = RepoLensSettings.getInstance(project)
            val selected = selectedFindings()
            val anyActive = selected.any { !settings.isFindingIgnored(it.id) }
            selected.forEach { settings.setFindingIgnored(it.id, anyActive) }
            applyFilters()
        }
        menu.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent) {
                val settings = RepoLensSettings.getInstance(project)
                val selected = selectedFindings()
                ignoreItem.isEnabled = selected.isNotEmpty()
                ignoreItem.text = if (selected.any { !settings.isFindingIgnored(it.id) }) {
                    "Ignore Finding"
                } else {
                    "Stop Ignoring"
                }
            }

            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent) = Unit
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent) = Unit
        })
        menu.add(ignoreItem)
        return menu
    }

    override fun analysisCancelled() {
        setRunning(false)
        statusLabel.text = "Analysis cancelled"
    }

    override fun analysisFailed(reason: String) {
        setRunning(false)
        statusLabel.text = reason
    }

    private var failedAnalyzerCount = 0
    private var skippedAnalyzers: List<com.kanicream.repolens.model.AnalyzerSkip> = emptyList()

    private fun setRunning(running: Boolean) {
        analyzeButton.isEnabled = !running
        scopeCombo.isEnabled = !running
        stopButton.isEnabled = running
    }

    private fun onSelectionChanged() {
        val selected = selectedFindings()
        copyButtons.values.forEach { it.isEnabled = selected.isNotEmpty() }
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

    private fun copySelection(style: CopyStyle) {
        val selected = selectedFindings()
        if (selected.isEmpty()) return
        FindingCopyService.getInstance(project).copy(selected, style, selectedScope().displayName) {
            statusLabel.text = "Copied ${selected.size} finding(s) (${style.actionName})"
        }
    }

    private fun selectedScope(): AnalysisScopeType =
        scopeCombo.selectedItem as? AnalysisScopeType ?: AnalysisScopeType.PROJECT

    private fun selectedFindings(): List<Finding> =
        table.selectedRows
            .map(table::convertRowIndexToModel)
            .mapNotNull(tableModel::findingAt)

    private fun detailText(finding: Finding): String = buildString {
        when (suppressionPolicy().suppressionOf(finding)) {
            SuppressionKind.IGNORED -> appendLine("[Ignored by you]")
            SuppressionKind.RULE -> appendLine("[Hidden by a suppress rule]")
            null -> {}
        }
        appendLine("${finding.checkName} (${finding.severity.displayName})")
        finding.confidence?.let { appendLine("Confidence: ${it.displayName}") }
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
        finding.metadata[CircularDependencyAnalyzer.METADATA_EVIDENCE]?.let { evidence ->
            appendLine()
            appendLine()
            appendLine("Cycle edges:")
            append(evidence)
        }
    }

    /** A Severity choice in the filter, including the "all" option. */
    private data class SeverityChoice(val label: String, val severity: Severity?)

    companion object {
        private const val NAVIGATE_ACTION_KEY = "repoLens.navigateToFinding"
        private const val ALL_CHECKS = "All checks"

        private val SEVERITY_CHOICES: List<SeverityChoice> =
            listOf(SeverityChoice("All", null)) +
                Severity.entries.map { SeverityChoice(it.displayName, it) }
    }
}
