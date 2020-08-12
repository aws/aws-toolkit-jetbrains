// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.ColumnInfo
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.LogStreamsStreamColumnRenderer
import software.aws.toolkits.jetbrains.utils.ui.setSelectionHighlighting
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class ColumnInfoDetails(private val fieldName: String) : ColumnInfo<List<ResultField>, String>(fieldName) {
    private val renderer = FieldColumnRenderer()
    override fun valueOf(item: List<ResultField>?): String? {
        if (item != null) {
            for (field in item) {
                if (field.field() == fieldName) {
                    return field.value()
                }
            }
        }
        //return item?.first { it.field() == fieldName }?.value()
        return null
    }
    override fun isCellEditable(item: List<ResultField>?): Boolean = false
    override fun getRenderer(item: List<ResultField>?): TableCellRenderer? = renderer
}

class FieldColumnRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val component = SimpleColoredComponent()
        component.append((value as? String)?.trim() ?: "")
        if (table == null) {
            return component
        }
        component.setSelectionHighlighting(table, isSelected)
        SpeedSearchUtil.applySpeedSearchHighlighting(table, component, true, isSelected)

        return component
    }
}
