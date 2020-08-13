// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.AnActionButton
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import javax.swing.JComponent
import javax.swing.JTable

class FilterDialog(private val project: Project) {
    val component: JComponent

    init {
        val table = TagFilterTable()
        table.setValues(ResourceFilterManager.getInstance(project).state.keys.toMutableList())
        component = table.component
    }
}

class TagFilterTable : ListTableWithButtons<String>() {
    init {
        tableView.tableHeader.reorderingAllowed = false
        tableView.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        // tableView.emptyText = "TODO empty text, add "
    }

    override fun createListModel(): ListTableModel<String> = ListTableModel(
        object : ColumnInfo<String, String>("TODO filters") {
            override fun valueOf(item: String?): String? = item
            override fun isCellEditable(item: String?): Boolean = true
        },
        object : ColumnInfo<String, String>("TODO type") {
            override fun valueOf(item: String?): String? = "$item type"
            override fun isCellEditable(item: String?): Boolean = false
        },
        object : ColumnInfo<String, String>("TODO description") {
            override fun valueOf(item: String?): String? = "$item description"
            override fun isCellEditable(item: String?): Boolean = false
        }
    )

    override fun createElement(): String = ""
    override fun isEmpty(element: String?): Boolean = element?.isEmpty() ?: true
    override fun cloneElement(variable: String?): String = variable ?: ""
    override fun canDeleteElement(selection: String?): Boolean = true
    override fun createExtraActions(): Array<AnActionButton> = arrayOf(
        AnActionButton.fromAction(object : DumbAwareAction("TODO edit", null, AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO open editor
            }
        })
    )
}

