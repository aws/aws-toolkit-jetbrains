// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import software.aws.toolkits.jetbrains.services.ecs.execution.ArtifactMapping
import javax.swing.JComponent

class FilterDialogWrapper(private val project: Project) : DialogWrapper(project) {
    val table = TemporaryTable()

    init {
        init()
        table.setValues(ResourceFilterManager.getInstance(project).getActiveFilters().map { entry ->
            TemporaryModel(true, entry.key, entry.value)
        })
    }

    override fun doOKAction() {
        val list = ResourceFilterManager.getInstance(project).getActiveFilters()
        list.clear()
        table.getItems().forEach {
            val key = it.key ?: return@forEach
            list[key] = it.values.toMutableList()
        }
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent? = table.component
}

data class TemporaryModel(
    var enabled: Boolean = false,
    var key: String? = null,
    var values: List<String> = listOf()
)

class TemporaryTable : ListTableWithButtons<TemporaryModel>() {
    override fun createListModel(): ListTableModel<*> = ListTableModel<ArtifactMapping>(
        object : ColumnInfo<TemporaryModel, String>("TODO enabled") {
            override fun valueOf(item: TemporaryModel): String? = item.enabled.toString()
            override fun isCellEditable(item: TemporaryModel): Boolean = true
            override fun setValue(item: TemporaryModel, value: String?) {
                item.enabled = value?.toBoolean() ?: false
            }
        },
        object : ColumnInfo<TemporaryModel, String>("TODO key") {
            override fun valueOf(item: TemporaryModel): String? = item.key
            override fun isCellEditable(item: TemporaryModel): Boolean = true
            override fun setValue(item: TemporaryModel, value: String?) {
                item.key = value
            }
        },
        object : ColumnInfo<TemporaryModel, String>("TODO value") {
            override fun valueOf(item: TemporaryModel): String? = item.values.joinToString(", ")
            override fun isCellEditable(item: TemporaryModel): Boolean = true
            override fun setValue(item: TemporaryModel, value: String) {
                item.values = value.split(",").map { it.trim() }
            }
        }
    )

    fun getItems() = elements.toList()

    override fun createElement(): TemporaryModel = TemporaryModel()

    override fun isEmpty(element: TemporaryModel?): Boolean = element?.key.isNullOrEmpty() || element?.values.isNullOrEmpty()

    override fun cloneElement(variable: TemporaryModel): TemporaryModel = variable.copy()

    override fun canDeleteElement(selection: TemporaryModel): Boolean = true
}
