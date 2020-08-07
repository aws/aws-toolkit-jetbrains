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
        table.setValues(ResourceFilterManager.getInstance(project).state.tags.map { entry ->
            TemporaryModel(entry.value.enabled, entry.key, entry.value.values)
        })
    }

    override fun doOKAction() {
        ResourceFilterManager.getInstance(project).state.tags = table.getItems().mapNotNull {
            val key = it.key ?: return@mapNotNull null
            key to TagFilter(it.enabled, it.values)
        }.toMap()
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent? = table.component
}

data class TemporaryModel(
    var enabled: Boolean = true,
    var key: String? = null,
    var values: List<String> = listOf()
)

class TemporaryTable : ListTableWithButtons<TemporaryModel>() {
    override fun createListModel(): ListTableModel<*> = ListTableModel<ArtifactMapping>(
        object : ColumnInfo<TemporaryModel, Boolean>("TODO enabled") {
            override fun getColumnClass(): Class<*> = Boolean::class.java
            override fun valueOf(item: TemporaryModel): Boolean? = item.enabled
            override fun isCellEditable(item: TemporaryModel): Boolean = true
            override fun setValue(item: TemporaryModel, value: Boolean?) {
                item.enabled = value ?: false
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
