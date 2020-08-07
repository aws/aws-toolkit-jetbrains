// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import javax.swing.JCheckBox
import javax.swing.JPanel

class FilterDialog(private val project: Project) {
    lateinit var panel: JPanel
    private lateinit var tagFilteringTextBox: JCheckBox
    private lateinit var tagTable: TagFilterTable

    private fun createUIComponents() {
        tagTable = TagFilterTable()
        val state = ResourceFilterManager.getInstance(project).state
        tagFilteringTextBox.isSelected = state.tagsEnabled
        tagTable.setValues(state.tags.map { entry ->
            TagFilterTableModel(entry.value.enabled, entry.key, entry.value.values)
        })
    }

    fun saveState() {
        ResourceFilterManager.getInstance(project).state.tags = tagTable.getItems().mapNotNull {
            val key = it.key ?: return@mapNotNull null
            key to TagFilter(it.enabled, it.values)
        }.toMap()

    }
}
