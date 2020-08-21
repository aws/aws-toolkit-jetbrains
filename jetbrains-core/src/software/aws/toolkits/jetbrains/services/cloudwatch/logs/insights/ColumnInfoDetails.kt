// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.ColumnInfo
import software.aws.toolkits.jetbrains.utils.ui.setSelectionHighlighting
import software.aws.toolkits.resources.message
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class ColumnInfoDetails(private val fieldName: String) : ColumnInfo<Map<String, String>, String>(fieldName) {
    private val renderer = FieldColumnRenderer()
    override fun valueOf(item: Map<String, String>?): String? {
        if (item != null) {
            return item[fieldName]
        }
        return null
    }
    override fun isCellEditable(item: Map<String, String>?): Boolean = false
    override fun getRenderer(item: Map<String, String>?): TableCellRenderer? = renderer
}

class LogEventKeyColumnDetails() : ColumnInfo <List<String>, String> (message("cloudwatch.logs.complete_log_event_field_name")) {
    private val renderer = DetailedLogEventFieldColumnRenderer()
    override fun valueOf(item: List<String>?): String? = item?.get(0)
    override fun isCellEditable(item: List<String>?): Boolean = false
    override fun getRenderer(item: List<String>?): TableCellRenderer? = renderer
}

class LogEventValueColumnDetails() : ColumnInfo <List<String>, String> (message("cloudwatch.logs.complete_log_event_field_value")) {
    private val renderer = LogEventColumnRenderer()
    override fun valueOf(item: List<String>?): String? = item?.get(1)
    override fun isCellEditable(item: List<String>?): Boolean = false
    override fun getRenderer(item: List<String>?): TableCellRenderer? = renderer
}

class LogEventColumnRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val component = SimpleColoredComponent()
        component.append((value as? String)?.trim() ?: "")
        if (table == null) {
            return component
        }
        component.setSelectionHighlighting(table, isSelected)
        return component
    }
}

class FieldColumnRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        if (table != null) {
            table.columnModel.getColumn(0).preferredWidth = 5
            table.columnModel.getColumn(0).minWidth = 5
            table.columnModel.getColumn(0).maxWidth = 5
        }
        val component = SimpleColoredComponent()
        component.append((value as? String)?.trim() ?: "")
        if (table == null) {
            return component
        }
        component.setSelectionHighlighting(table, isSelected)
        return component
    }
}
class DetailedLogEventFieldColumnRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        if (table != null) {
            table.columnModel.getColumn(0).preferredWidth = 150
            table.columnModel.getColumn(0).minWidth = 150
            table.columnModel.getColumn(0).maxWidth = 150
        }
        val component = SimpleColoredComponent()
        component.append((value as? String)?.trim() ?: "")
        if (table == null) {
            return component
        }
        component.setSelectionHighlighting(table, isSelected)
        return component
    }
}
