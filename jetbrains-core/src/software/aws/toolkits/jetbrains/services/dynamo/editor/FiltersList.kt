// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamo.editor

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel

class FiltersList {
    data class Filter(val attributeName: String)

    private val table = JBTable(ListTableModel<Filter>())
    private val toolbar = ToolbarDecorator.createDecorator(table)

    init {
        table.emptyText.text = "No filters applied"

        table.visibleRowCount = 4

        toolbar.disableUpDownActions()
    }

    fun getComponent() = toolbar.createPanel()

    fun getFilters(): List<Filter> = emptyList()
}
