// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import software.aws.toolkits.resources.message
import javax.swing.JPanel
import javax.swing.JTextField

class TagFilterDialog : FilterDialog {
    override lateinit var component: JPanel
    private lateinit var filterName: JTextField
    private lateinit var keyBox: JTextField
    private lateinit var valuesField: JBTextField
    private var enabled = true

    init {
        valuesField.emptyText.text = message("explorer.filter.tag.any_value")
        // TODO load autocomplete suggestions in next PR
    }

    override fun validate(): ValidationInfo? {
        if (filterName.text.isBlank()) {
            return ValidationInfo(message("explorer.filter.validation.no_filter_name"))
        }
        if (keyBox.text.isBlank()) {
            return ValidationInfo(message("explorer.filter.validation.no_key_entered"))
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
