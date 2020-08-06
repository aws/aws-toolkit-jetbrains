// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.util.ui.ColumnInfo
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.LogStreamsStreamColumnRenderer
import javax.swing.table.TableCellRenderer

class ColumnInfoDetails(private val fieldName: String) : ColumnInfo<List<ResultField>, String>(fieldName) {
    private val renderer = LogStreamsStreamColumnRenderer()
    override fun valueOf(item: List<ResultField>?): String? {
        if (item != null) {
            for (field in item){
                if(field.field() == fieldName){
                    return field.value()
                }
            }
        }
        return null
    }
    override fun isCellEditable(item: List<ResultField>?): Boolean = false
    override fun getRenderer(item: List<ResultField>?): TableCellRenderer? = renderer

}
