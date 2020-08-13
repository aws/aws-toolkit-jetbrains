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

class TagFilterDialog(private val project: Project): FilterDialog {
    override lateinit var component: JPanel
    override fun validate() {
    }

    override fun save() {
        ResourceFilterManager.getInstance(project).state[filterName.text] = ResourceFilter(
            enabled = true,
            tags = mapOf(keyBox.text to listOf(""))
        )
    }

    private lateinit var filterName: JTextField
    private lateinit var keyBox: JTextField

    init {
        // TODO load autocomplete suggestions
    }
}
