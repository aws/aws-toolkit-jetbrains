// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackEvent
import java.awt.Component
import java.awt.Font
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

internal object StackEventsTableComponents {
    // Column indices for events table
    const val ARROW_COLUMN = 0
    const val OPERATION_COLUMN = 1
    const val TIMESTAMP_COLUMN = 2
    const val STATUS_COLUMN = 3
    const val STATUS_REASON_COLUMN = 4
    const val HOOK_COLUMN = 5
}

// Data class to represent a table row (either parent group or child event)
internal data class EventTableRow(
    val event: StackEvent,
    val isParent: Boolean,
    val isVisible: Boolean = true,
    val parentIndex: Int? = null,
    val childCount: Int = 0,
)

internal class ExpandableEventsTableModel : AbstractTableModel() {
    private var allEvents: List<StackEvent> = emptyList()
    private var displayRows: MutableList<EventTableRow> = mutableListOf()
    val expandedGroups = mutableSetOf<String>() // Make public for renderer access
    private var hasHooks = false
    private val columnNames = arrayOf("", "Operation", "Timestamp", "Status", "Status Reason", "Hook Invocation")
    private var currentPage = 0
    private val eventsPerPage = 50

    fun setEvents(events: List<StackEvent>) {
        allEvents = events
        hasHooks = events.any { it.hookType != null }

        currentPage = 0 // Reset to first page
        rebuildDisplayRows()
        fireTableDataChanged()

        if (events.isEmpty()) {
            return
        }

        // Group events by Operation ID
        val grouped = events.groupBy { it.operationId ?: "No Operation" }

        // Auto-expand first group
        if (grouped.isNotEmpty()) {
            val firstOperationId = grouped.keys.first()
            expandedGroups.add(firstOperationId)
            rebuildDisplayRows()
            fireTableDataChanged()
        }
    }

    fun setCurrentPage(page: Int) {
        currentPage = page
        rebuildDisplayRows()
        fireTableDataChanged()
    }

    private fun rebuildDisplayRows() {
        displayRows.clear()

        if (allEvents.isEmpty()) {
            return
        }

        // Calculate pagination for events
        val startIndex = currentPage * eventsPerPage
        val endIndex = minOf(startIndex + eventsPerPage, allEvents.size)
        val pageEvents = allEvents.subList(startIndex, endIndex)

        // Group events by Operation ID
        val grouped = pageEvents.groupBy { it.operationId ?: "No Operation" }

        grouped.forEach { (operationId, operationEvents) ->
            if (operationEvents.size == 1 && operationId == "No Operation") {
                // Single event without operation ID - add directly
                displayRows.add(EventTableRow(operationEvents.first(), isParent = false))
            } else {
                // Add parent row
                val parentEvent = operationEvents.first()
                displayRows.add(
                    EventTableRow(
                        event = parentEvent,
                        isParent = true,
                        childCount = operationEvents.size
                    )
                )

                // Add child rows (visible only if expanded)
                val isExpanded = expandedGroups.contains(operationId)
                operationEvents.forEach { event ->
                    displayRows.add(
                        EventTableRow(
                            event = event,
                            isParent = false,
                            isVisible = isExpanded
                        )
                    )
                }
            }
        }
    }

    fun toggleExpansion(row: Int) {
        val visibleRows = displayRows.filter { it.isVisible }
        if (row >= visibleRows.size) return

        val clickedRow = visibleRows[row]
        if (!clickedRow.isParent) return

        val operationId = clickedRow.event.operationId ?: return

        if (expandedGroups.contains(operationId)) {
            expandedGroups.remove(operationId)
        } else {
            expandedGroups.add(operationId)
        }

        rebuildDisplayRows()
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = displayRows.count { it.isVisible }
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val visibleRows = displayRows.filter { it.isVisible }
        if (rowIndex >= visibleRows.size) return ""

        val row = visibleRows[rowIndex]
        val event = row.event

        return when (columnIndex) {
            StackEventsTableComponents.ARROW_COLUMN -> if (row.isParent) {
                val isExpanded = (row.event.operationId ?: "") in expandedGroups
                if (isExpanded) "⏷" else "⏵"
            } else {
                ""
            }
            StackEventsTableComponents.OPERATION_COLUMN -> if (row.isParent) {
                row.event.operationId ?: "Unknown"
            } else {
                "  ${row.event.operationId ?: "-"}" // Indent child rows
            }
            StackEventsTableComponents.TIMESTAMP_COLUMN -> event.timestamp ?: ""
            StackEventsTableComponents.STATUS_COLUMN -> event.resourceStatus ?: ""
            StackEventsTableComponents.STATUS_REASON_COLUMN -> event.resourceStatusReason?.takeIf { it.isNotEmpty() } ?: "-"
            StackEventsTableComponents.HOOK_COLUMN -> if (event.hookType != null) {
                "${event.hookType} (${event.hookStatus ?: "Unknown"})"
            } else {
                "-"
            }
            else -> ""
        }
    }

    fun getRowAt(rowIndex: Int): EventTableRow? {
        val visibleRows = displayRows.filter { it.isVisible }
        return if (rowIndex < visibleRows.size) visibleRows[rowIndex] else null
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
}

internal class OperationCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val model = table.model as ExpandableEventsTableModel
        val tableRow = model.getRowAt(row)
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (tableRow?.isParent == true) {
            val operationId = tableRow.event.operationId ?: ""
            text = "<html><a href=\"#\">$operationId</a> <span style=\"color:gray;\">(${tableRow.childCount} events)</span></html>"
            font = font.deriveFont(Font.BOLD)

            // Remove click handler since table handles it
        } else {
            font = font.deriveFont(Font.PLAIN)
            val operationId = tableRow?.event?.operationId
            if (!isSelected && !operationId.isNullOrEmpty() && operationId != "-") {
                foreground = JBColor.BLUE
            }
        }

        return component
    }
}

internal class EventsTableCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (!isSelected && column == StackEventsTableComponents.STATUS_COLUMN) { // Status column
            val status = value?.toString() ?: ""
            foreground = when {
                status.contains("COMPLETE") && !status.contains("ROLLBACK") -> JBColor.GREEN
                status.contains("FAILED") || status.contains("ROLLBACK") -> JBColor.RED
                status.contains("PROGRESS") -> JBColor.ORANGE
                else -> UIUtil.getTableForeground()
            }
        } else if (!isSelected) {
            foreground = UIUtil.getTableForeground()
        }

        return component
    }
}
