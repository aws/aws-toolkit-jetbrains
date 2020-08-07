// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class FilterDialog(private val project: Project) {
    lateinit var panel: JPanel
    lateinit var table: TagFilterTable
    private lateinit var tagFilteringEnabled: JCheckBox
    private lateinit var tagTable: JComponent

    private fun createUIComponents() {
        table = TagFilterTable()
        tagTable = table.component
        val state = ResourceFilterManager.getInstance(project).state
        table.setValues(state.tags.map { entry ->
            TagFilterTableModel(entry.value.enabled, entry.key, entry.value.values)
        })
    }

    init {
        tagFilteringEnabled.isSelected = ResourceFilterManager.getInstance(project).state.tagsEnabled
    }

    fun saveState() {
        ResourceFilterManager.getInstance(project).state.tags = table.getItems().mapNotNull {
            val key = it.key ?: return@mapNotNull null
            key to TagFilter(it.enabled, it.values)
        }.toMap()
        ResourceFilterManager.getInstance(project).state.tagsEnabled = tagFilteringEnabled.isSelected
    }
}
