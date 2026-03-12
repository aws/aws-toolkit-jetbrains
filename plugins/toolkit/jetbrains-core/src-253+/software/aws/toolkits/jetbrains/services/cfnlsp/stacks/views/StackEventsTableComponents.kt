// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackEvent
import java.awt.Component
import java.awt.Font
import javax.swing.Icon
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

    // Pagination constants
    const val EVENTS_PER_PAGE = 50
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
    private val displayRows: MutableList<EventTableRow> = mutableListOf()
    val expandedGroups = mutableSetOf<String>() // Make public for renderer access
    private var hasHooks = false
    private val baseColumnNames = arrayOf("", "Operation ID", "Timestamp", "Status", "Status Reason")
    private val hookColumnName = "Hook Invocation"
    private var currentPage = 0
    private val eventsPerPage = StackEventsTableComponents.EVENTS_PER_PAGE

    fun setEvents(events: List<StackEvent>, errorMessage: String? = null) {
        allEvents = events
        hasHooks = false // Reset hook detection

        currentPage = 0 // Reset to first page
        rebuildDisplayRows(errorMessage)

        if (events.isEmpty()) {
            fireTableStructureChanged()
            return
        }

        // Group events by Operation ID
        val grouped = events.groupBy { it.operationId ?: "No Operation" }

        // Auto-expand first group
        if (grouped.isNotEmpty()) {
            val firstOperationId = grouped.keys.first()
            expandedGroups.add(firstOperationId)
            rebuildDisplayRows()
        }

        fireTableStructureChanged() // Use structure changed since column count may change
    }

    fun setCurrentPage(page: Int) {
        val oldHasHooks = hasHooks
        currentPage = page
        rebuildDisplayRows()

        // Fire structure changed if column count changed, otherwise just data changed
        if (oldHasHooks != hasHooks) {
            fireTableStructureChanged()
        } else {
            fireTableDataChanged()
        }
    }

    private fun rebuildDisplayRows(errorMessage: String? = null) {
        displayRows.clear()

        if (errorMessage != null) {
            val errorEvent = StackEvent(
                operationId = "",
                resourceType = "",
                resourceStatus = "",
                timestamp = "",
                resourceStatusReason = errorMessage
            )
            displayRows.add(EventTableRow(errorEvent, isParent = false))
            return
        }

        if (allEvents.isEmpty()) {
            return
        }

        // Calculate pagination for events
        val startIndex = currentPage * eventsPerPage
        val endIndex = minOf(startIndex + eventsPerPage, allEvents.size)
        val pageEvents = allEvents.subList(startIndex, endIndex)

        // Check if current page has hooks
        hasHooks = pageEvents.any { it.hookType != null }

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
        fireTableDataChanged() // Only data changes, not structure
    }

    override fun getRowCount(): Int = displayRows.count { it.isVisible }
    override fun getColumnCount(): Int = if (hasHooks) baseColumnNames.size + 1 else baseColumnNames.size
    override fun getColumnName(column: Int): String =
        if (column < baseColumnNames.size) {
            baseColumnNames[column]
        } else {
            hookColumnName
        }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val visibleRows = displayRows.filter { it.isVisible }
        if (rowIndex >= visibleRows.size) return ""

        val row = visibleRows[rowIndex]
        val event = row.event

        return when (columnIndex) {
            StackEventsTableComponents.ARROW_COLUMN -> if (row.isParent) {
                val isExpanded = (row.event.operationId.orEmpty()) in expandedGroups
                if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            } else {
                ""
            }
            StackEventsTableComponents.OPERATION_COLUMN -> if (row.isParent) {
                row.event.operationId ?: "Unknown"
            } else {
                "  ${row.event.operationId ?: "-"}" // Indent child rows
            }
            StackEventsTableComponents.TIMESTAMP_COLUMN -> event.timestamp.orEmpty()
            StackEventsTableComponents.STATUS_COLUMN -> event.resourceStatus.orEmpty()
            StackEventsTableComponents.STATUS_REASON_COLUMN -> event.resourceStatusReason?.takeIf { it.isNotEmpty() } ?: "-"
            baseColumnNames.size -> if (hasHooks) {
                if (event.hookType != null) {
                    "${event.hookType} (${event.hookStatus ?: "Unknown"})"
                } else {
                    "-"
                }
            } else {
                ""
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
            val operationId = tableRow.event.operationId.orEmpty()
            text = "<html><a href=\"#\">$operationId</a></html>"
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

        // Handle both icons and strings in arrow column
        if (column == StackEventsTableComponents.ARROW_COLUMN) {
            val label = JBLabel()
            when (value) {
                is Icon -> {
                    label.icon = value
                    label.text = null
                    label.horizontalAlignment = CENTER
                }
                is String -> {
                    label.text = value
                    label.icon = null
                    label.horizontalAlignment = CENTER
                }
            }
            return label
        }

        if (!isSelected && column == StackEventsTableComponents.STATUS_COLUMN) { // Status column
            val status = value?.toString().orEmpty()
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
