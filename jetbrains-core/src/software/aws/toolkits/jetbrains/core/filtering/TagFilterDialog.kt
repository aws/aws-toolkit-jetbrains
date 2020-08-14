// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import javax.swing.JPanel
import javax.swing.JTextField

class TagFilterDialog(private val project: Project) : FilterDialog {
    override lateinit var component: JPanel
    private lateinit var filterName: JTextField
    private lateinit var keyBox: JTextField
    private lateinit var valuesField: JBTextField

    init {
        valuesField.emptyText.text = "TODO all values"
    }

    override fun validate() {
    }

    override fun save() {
        val tags = if (valuesField.text.isBlank()) {
            listOf()
        } else {
            valuesField.text.split(",").map { it.trim() }
        }
        ResourceFilterManager.getInstance(project).state[filterName.text] = TagFilter(
            enabled = true,
            tagKey = keyBox.text,
            tagValues = tags
        )
    }


    init {
        // TODO load autocomplete suggestions
    }
}
