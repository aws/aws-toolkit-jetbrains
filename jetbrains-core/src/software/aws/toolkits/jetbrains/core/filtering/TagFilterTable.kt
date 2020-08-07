// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import software.aws.toolkits.jetbrains.services.ecs.execution.ArtifactMapping

class TagFilterTable : ListTableWithButtons<TagFilterTableModel>() {
    override fun createListModel(): ListTableModel<*> = ListTableModel<ArtifactMapping>(
        object : ColumnInfo<TagFilterTableModel, Boolean>("TODO enabled") {
            override fun getColumnClass(): Class<*> = Boolean::class.java
            override fun valueOf(item: TagFilterTableModel): Boolean? = item.enabled
            override fun isCellEditable(item: TagFilterTableModel): Boolean = true
            override fun setValue(item: TagFilterTableModel, value: Boolean?) {
                item.enabled = value ?: false
            }
        },
        object : ColumnInfo<TagFilterTableModel, String>("TODO key") {
            override fun valueOf(item: TagFilterTableModel): String? = item.key
            override fun isCellEditable(item: TagFilterTableModel): Boolean = true
            override fun setValue(item: TagFilterTableModel, value: String?) {
                item.key = value
            }
        },
        object : ColumnInfo<TagFilterTableModel, String>("TODO value") {
            override fun valueOf(item: TagFilterTableModel): String? = item.values.joinToString(", ")
            override fun isCellEditable(item: TagFilterTableModel): Boolean = true
            override fun setValue(item: TagFilterTableModel, value: String) {
                item.values = value.split(",").map { it.trim() }
            }
        }
    )

    fun getItems() = elements.toList()

    override fun createElement(): TagFilterTableModel = TagFilterTableModel()

    override fun isEmpty(element: TagFilterTableModel?): Boolean = element?.key.isNullOrEmpty() || element?.values.isNullOrEmpty()

    override fun cloneElement(variable: TagFilterTableModel): TagFilterTableModel = variable.copy()

    override fun canDeleteElement(selection: TagFilterTableModel): Boolean = true
}

data class TagFilterTableModel(
    var enabled: Boolean = true,
    var key: String? = null,
    var values: List<String> = listOf()
)

