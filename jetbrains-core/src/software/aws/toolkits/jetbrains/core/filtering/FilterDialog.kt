// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import software.aws.toolkits.jetbrains.services.ecs.EcsUtils
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTextField

class FilterDialog(private val project: Project) {
    lateinit var component: JPanel
    lateinit var filterType: JComboBox<FilterTypes>
    lateinit var tags: JPanel
    lateinit var stacks: JPanel
    lateinit var keyBox: JTextField
    lateinit var stackSelector: ResourceSelector<StackSummary>

    init {
        stacks.isVisible = false
        tags.isVisible = true
    }

    private fun createUIComponents() {
        filterType = ComboBox(FilterTypes.values())
        filterType.addActionListener {
            when (filterType.selectedItem) {
                FilterTypes.Tag -> showTag()
                FilterTypes.Stack -> showStack()
            }
        }
        stackSelector = ResourceSelector.builder(project)
            .resource { CloudFormationResources.ACTIVE_STACKS }
            .customRenderer { value, component -> component.append(value.stackId()); component }
            .build()
    }

    private fun showTag() {
        stacks.isVisible = false
        tags.isVisible = true
    }

    private fun showStack() {
        stacks.isVisible = true
        tags.isVisible = false
    }

    fun selected(): FilterTypes = filterType.selectedItem as? FilterTypes ?: throw IllegalStateException("Combo box does not have right items")

    // TODO move this
    enum class FilterTypes(val text: String) {
        Tag("TODO localize Tag based filtering"),
        Stack("TODO localize CloudFormation Stack")
    }
}
