// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

internal class ResourceTypeSelectionDialog(
    project: Project,
    private val availableTypes: List<String>,
    selectedTypes: Set<String> = emptySet(),
) : DialogWrapper(project) {
    var selectedResourceTypes: List<String> = emptyList()
        private set

    private val searchField = SearchTextField(false)
    private val currentSelections = selectedTypes.toMutableSet()
    private val tableModel = ResourceTypeTableModel(availableTypes, currentSelections)
    private val table = createTable()

    init {
        title = message("cloudformation.explorer.resources.dialog.title")
        init()
        setupSearch()
        filterList()
    }

    override fun getPreferredFocusedComponent() = searchField

    private fun createTable() = JBTable(tableModel).apply {
        setShowGrid(false)

        tableHeader = null
        rowHeight = TABLE_ROW_HEIGHT

        setDefaultRenderer(Boolean::class.java, ResourceTypeCellRenderer(tableModel))

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val table = e.source as JTable
                val row = table.rowAtPoint(e.point)
                if (row >= 0) {
                    val currentValue = tableModel.getValueAt(row, 0)
                    tableModel.setValueAt(!currentValue, row, 0)
                    searchField.requestFocus()
                }
            }
        })
    }

    private fun setupSearch() {
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterList()
            override fun removeUpdate(e: DocumentEvent?) = filterList()
            override fun changedUpdate(e: DocumentEvent?) = filterList()
        })
    }

    private fun filterList() {
        val searchText = searchField.text
        val filteredTypes = if (searchText.isEmpty()) {
            availableTypes
        } else {
            val matcher = NameUtil.buildMatcher("*$searchText*", NameUtil.MatchingCaseSensitivity.NONE)
            availableTypes.filter { matcher.matches(it) }
        }

        tableModel.updateFilter(filteredTypes)
    }

    override fun createCenterPanel() = panel {
        row {
            cell(searchField).align(AlignX.FILL)
        }
        row {
            scrollCell(table).align(Align.FILL)
        }.resizableRow()
    }.apply {
        preferredSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)
    }

    override fun doOKAction() {
        selectedResourceTypes = currentSelections.toList()
        super.doOKAction()
    }

    companion object {
        private const val TABLE_ROW_HEIGHT = 24
        private const val DIALOG_WIDTH = 400
        private const val DIALOG_HEIGHT = 300
    }
}

private class ResourceTypeTableModel(
    availableTypes: List<String>,
    private val selections: MutableSet<String>,
) : AbstractTableModel() {
    private var filteredTypes = availableTypes.toList()

    override fun getRowCount() = filteredTypes.size
    override fun getColumnCount() = 1
    override fun getColumnClass(col: Int) = Boolean::class.java
    override fun getValueAt(row: Int, col: Int) = filteredTypes[row] in selections
    override fun isCellEditable(row: Int, col: Int) = true

    override fun setValueAt(value: Any, row: Int, col: Int) {
        if (value is Boolean) {
            val type = filteredTypes[row]

            if (value) selections.add(type) else selections.remove(type)

            fireTableCellUpdated(row, col)
        }
    }

    fun updateFilter(types: List<String>) {
        filteredTypes = types
        fireTableDataChanged()
    }

    fun getResourceType(row: Int) = filteredTypes[row]
}

private class ResourceTypeCellRenderer(
    private val tableModel: ResourceTypeTableModel,
) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val label = JBLabel(tableModel.getResourceType(row))

        label.icon = if (value as Boolean) AllIcons.Actions.Checked else EmptyIcon.ICON_16
        label.iconTextGap = ICON_TEXT_GAP
        label.border = JBUI.Borders.emptyLeft(LEFT_BORDER)
        label.background = if (isSelected) table.selectionBackground else table.background
        label.foreground = if (isSelected) table.selectionForeground else table.foreground
        label.isOpaque = true

        return label
    }

    companion object {
        private const val ICON_TEXT_GAP = 8
        private const val LEFT_BORDER = 8
    }
}
