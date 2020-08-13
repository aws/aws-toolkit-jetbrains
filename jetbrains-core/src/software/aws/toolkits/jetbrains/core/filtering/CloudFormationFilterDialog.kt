// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import javax.swing.JPanel
import javax.swing.JTextField

class CloudFormationFilterDialog(private val project: Project): FilterDialog {
    override lateinit var component: JPanel
    override fun validate() {
    }

    override fun save() {
        ResourceFilterManager.getInstance(project).state[filterName.text] = ResourceFilter(
            enabled = true,
            stacks = listOf(stackSelector.selected()?.stackId() ?: "")
        )
    }

    private lateinit var stackSelector: ResourceSelector<StackSummary>
    private lateinit var filterName: JTextField

    private fun createUIComponents() {
        stackSelector = ResourceSelector.builder(project)
            .resource { CloudFormationResources.ACTIVE_STACKS }
            .customRenderer { value, component -> component.append(value.stackId()); component }
            .build()
    }
}
