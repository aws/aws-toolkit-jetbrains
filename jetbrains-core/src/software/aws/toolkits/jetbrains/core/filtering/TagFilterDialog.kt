// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TextFieldWithAutoCompletion
import software.aws.toolkits.resources.message
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JPanel
import javax.swing.JTextField

class TagFilterDialog(private val project: Project) : FilterDialogPanel {
    override lateinit var component: JPanel
    private lateinit var filterName: JTextField
    private lateinit var keyBox: TextFieldWithAutoCompletion<String>
    private lateinit var valuesField: TextFieldWithAutoCompletion<String>
    private var enabled = true

    private fun createUIComponents() {
        keyBox = TextFieldWithAutoCompletion(project, KeyProvider(project), false, "")
        valuesField = TextFieldWithAutoCompletion(project, ValueProvider(project, ""), false, "")
        keyBox.addFocusListener(
            object : FocusListener {
                override fun focusGained(e: FocusEvent?) = Unit
                override fun focusLost(e: FocusEvent?) {
                    valuesField.installProvider(ValueProvider(project, keyBox.text))
                }
            }
        )
    }

    override fun validate(): ValidationInfo? {
        if (filterName.text.isBlank()) {
            return ValidationInfo(message("explorer.filter.validation.no_filter_name"), filterName)
        }
        if (keyBox.text.isBlank()) {
            return ValidationInfo(message("explorer.filter.validation.no_key_entered"), keyBox)
        }
        return null
    }

    override fun save(): Pair<String, ResourceFilter> {
        val tags = if (valuesField.text.isBlank()) {
            listOf()
        } else {
            valuesField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        return filterName.text to TagFilter(enabled = enabled, tagKey = keyBox.text, tagValues = tags)
    }

    override fun load(name: String, filter: ResourceFilter) {
        if (filter !is TagFilter) throw IllegalStateException("filter passed into TagFilterDialog is not a TagFilter: $filter")
        filterName.text = name
        enabled = filter.enabled
        keyBox.text = filter.tagKey
        valuesField.text = filter.tagValues.joinToString(", ")
    }
}
