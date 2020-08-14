// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import javax.swing.JPanel
import javax.swing.JTextField

class CloudFormationFilterDialog(private val project: Project) : FilterDialog {
    override lateinit var component: JPanel
    private lateinit var stackSelector: ResourceSelector<StackSummary>
    private lateinit var filterName: JTextField
    private var enabled = true

    private fun createUIComponents() {
        stackSelector = ResourceSelector.builder(project)
            .resource { CloudFormationResources.ACTIVE_STACKS }
            .customRenderer { value, component -> component.append(value.stackId()); component }
            .build()
    }

    override fun validate(): ValidationInfo? {
        return null
    }

    override fun save(): Pair<String, ResourceFilter> = filterName.text to StackFilter(
        enabled = enabled,
        stackID = stackSelector.selected()?.stackId() ?: ""
    )

    override fun load(name: String, filter: ResourceFilter) {
        if (filter !is StackFilter) return
        filterName.text = name
        enabled = filter.enabled
        stackSelector.selectedItem { it.stackId() == filter.stackID }
    }
}
