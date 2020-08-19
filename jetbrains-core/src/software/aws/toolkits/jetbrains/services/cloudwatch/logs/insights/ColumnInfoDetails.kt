// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.ColumnInfo
import software.aws.toolkits.jetbrains.utils.ui.setSelectionHighlighting
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

class LogEventColumnDetails() : ColumnInfo <String,String> ("Log Event") {
    private val renderer = LogEventColumnRenderer()
    override fun valueOf(item: String?): String? = item
    override fun isCellEditable(item: String?): Boolean = false
    override fun getRenderer(item: String?): TableCellRenderer? = renderer
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
