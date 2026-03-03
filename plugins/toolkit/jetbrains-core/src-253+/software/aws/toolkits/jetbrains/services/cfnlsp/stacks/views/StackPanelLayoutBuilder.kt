// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackOutput
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackEvent
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal object StackPanelLayoutBuilder {

    private const val DEFAULT_PADDING = 20
    private const val FIELD_SPACING = 12

    fun createTitleLabel(text: String): JBLabel = JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = font.deriveFont(Font.BOLD)
    }

    fun createFormPanel(padding: Int = DEFAULT_PADDING): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
        border = JBUI.Borders.empty(padding)
    }

    fun addLabeledField(
        parent: JPanel,
        gbc: GridBagConstraints,
        startRow: Int,
        labelText: String,
        component: JComponent,
        fillNone: Boolean = false,
        isLast: Boolean = false,
    ): Int {
        // Add label
        gbc.gridx = 0
        gbc.gridy = startRow
        gbc.insets = JBUI.emptyInsets()
        parent.add(createTitleLabel(labelText), gbc)

        // Add component
        gbc.gridy = startRow + 1
        gbc.insets = if (isLast) JBUI.emptyInsets() else JBUI.insetsBottom(FIELD_SPACING)
        if (fillNone) {
            gbc.fill = GridBagConstraints.NONE
            gbc.anchor = GridBagConstraints.WEST
        }
        parent.add(component, gbc)

        // Reset constraints
        if (fillNone) {
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.anchor = GridBagConstraints.NORTHWEST
        }

        return startRow + 2
    }

    fun addFiller(parent: JPanel, gbc: GridBagConstraints, row: Int) {
        gbc.gridy = row + 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        parent.add(JPanel(), gbc)
    }

    fun createTableWithPaginationPanel(
        title: String,
        consoleLink: JComponent,
        pageLabel: JComponent,
        prevButton: JButton,
        nextButton: JButton,
        table: JBTable,
        countLabel: JComponent? = null,
    ): JComponent = panel {
        row {
            label(title).bold()
            cell(consoleLink)
            countLabel?.let { cell(it) }
            cell(
                JBPanel<JBPanel<*>>().apply {
                    layout = FlowLayout(FlowLayout.RIGHT)
                    add(pageLabel)
                    add(prevButton)
                    add(nextButton)
                }
            ).align(AlignX.FILL)
        }
        row {
            scrollCell(table).align(Align.FILL)
        }.resizableRow()
    }

    fun createOutputsTable(): JBTable = JBTable().apply {
        setShowGrid(true)
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        // Set initial empty state
        model = object : DefaultTableModel(
            arrayOf(arrayOf("No outputs found", "", "", "")),
            arrayOf("Key", "Value", "Description", "Export Name")
        ) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
    }

    fun updateOutputsTable(table: JBTable, outputs: List<StackOutput>, errorMessage: String? = null) {
        val columnNames = arrayOf("Key", "Value", "Description", "Export Name")
        val data = when {
            errorMessage != null -> arrayOf(arrayOf(errorMessage, "", "", ""))
            outputs.isEmpty() -> arrayOf(arrayOf("No outputs found", "", "", ""))
            else -> outputs.map { output ->
                arrayOf(
                    output.outputKey,
                    output.outputValue,
                    output.description ?: "",
                    output.exportName ?: ""
                )
            }.toTypedArray()
        }

        table.model = object : DefaultTableModel(data, columnNames) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
    }

    fun createTablePanel(
        title: String,
        consoleLink: JComponent,
        outputCountLabel: JComponent,
        table: JBTable,
    ): JComponent = panel {
        row {
            label(title).bold()
            cell(consoleLink)
            cell(outputCountLabel).align(AlignX.RIGHT)
        }
        row {
            scrollCell(table).align(Align.FILL)
        }.resizableRow()
    }

    fun createEventsTable(onOperationClick: ((String) -> Unit)? = null): JBTable {
        val tableModel = ExpandableEventsTableModel()
        return JBTable(tableModel).apply {
            setShowGrid(true)
            autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

            // Custom renderer for the first column (Arrow) for expand/collapse
            columnModel.getColumn(StackEventsTableComponents.ARROW_COLUMN).cellRenderer = DefaultTableCellRenderer()
            columnModel.getColumn(StackEventsTableComponents.ARROW_COLUMN).preferredWidth = 25
            columnModel.getColumn(StackEventsTableComponents.ARROW_COLUMN).maxWidth = 50

            // Custom renderer for the second column (Operation) to show hyperlinks
            columnModel.getColumn(StackEventsTableComponents.OPERATION_COLUMN).cellRenderer = OperationCellRenderer()

            // Status column renderer for colors
            columnModel.getColumn(StackEventsTableComponents.STATUS_COLUMN).cellRenderer = EventsTableCellRenderer()

            // Click handler for expand/collapse (any column) and hyperlinks
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)
                    if (row >= 0) {
                        val model = model as ExpandableEventsTableModel
                        val tableRow = model.getRowAt(row)

                        if (tableRow?.isParent == true) {
                            if (col == StackEventsTableComponents.OPERATION_COLUMN) { // Operation column - handle hyperlink first
                                val operationId = tableRow.event.operationId
                                if (!operationId.isNullOrEmpty()) {
                                    onOperationClick?.invoke(operationId)
                                }
                                // Don't expand/collapse for hyperlink clicks
                            } else {
                                // Any other column - expand/collapse
                                model.toggleExpansion(row)
                            }
                        } else if (col == StackEventsTableComponents.OPERATION_COLUMN) { // Child row operation column
                            val operationId = tableRow?.event?.operationId
                            if (!operationId.isNullOrEmpty() && operationId != "-") {
                                onOperationClick?.invoke(operationId)
                            }
                        }
                    }
                }
            })
        }
    }

    fun updateEventsTable(table: JBTable, events: List<StackEvent>) {
        val model = table.model as ExpandableEventsTableModel
        model.setEvents(events)
    }

    fun updateEventsTablePage(table: JBTable, page: Int) {
        val model = table.model as ExpandableEventsTableModel
        model.setCurrentPage(page)
    }
}
