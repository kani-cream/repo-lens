package com.kanicream.repolens.ui

import com.kanicream.repolens.RepoLensBundle
import com.kanicream.repolens.format.MetricFormat
import com.kanicream.repolens.uiName
import com.kanicream.repolens.model.Finding
import javax.swing.table.AbstractTableModel

/** Read-only table model over the current findings. */
internal class FindingTableModel : AbstractTableModel() {

    private var findings: List<Finding> = emptyList()

    fun setFindings(findings: List<Finding>) {
        this.findings = findings
        fireTableDataChanged()
    }

    fun findingAt(rowIndex: Int): Finding? = findings.getOrNull(rowIndex)

    override fun getRowCount(): Int = findings.size

    override fun getColumnCount(): Int = COLUMNS.size

    override fun getColumnName(column: Int): String = COLUMNS[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val finding = findings[rowIndex]
        return when (columnIndex) {
            COLUMN_SEVERITY -> finding.severity.uiName()
            COLUMN_CHECK -> finding.checkName
            COLUMN_FILE -> finding.location.filePath
            COLUMN_SYMBOL -> finding.symbol?.displayName.orEmpty()
            COLUMN_LOCATION -> finding.location.lineRangeText
            COLUMN_VALUE_THRESHOLD -> valueAndThreshold(finding)
            else -> ""
        }
    }

    private fun valueAndThreshold(finding: Finding): String {
        val value = finding.measuredValue?.let(MetricFormat::format) ?: return ""
        val threshold = finding.threshold?.let(MetricFormat::format) ?: return value
        return "$value / $threshold"
    }

    companion object {
        const val COLUMN_SEVERITY = 0
        const val COLUMN_CHECK = 1
        const val COLUMN_FILE = 2
        const val COLUMN_SYMBOL = 3
        const val COLUMN_LOCATION = 4
        const val COLUMN_VALUE_THRESHOLD = 5

        private val COLUMNS = arrayOf(
            RepoLensBundle.message("column.severity"),
            RepoLensBundle.message("column.check"),
            RepoLensBundle.message("column.file"),
            RepoLensBundle.message("column.symbol"),
            RepoLensBundle.message("column.location"),
            RepoLensBundle.message("column.value.threshold"),
        )
    }
}
