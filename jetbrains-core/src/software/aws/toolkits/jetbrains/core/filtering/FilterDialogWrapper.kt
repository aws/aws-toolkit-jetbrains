// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import software.aws.toolkits.jetbrains.core.explorer.redrawAwsTree
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class FilterDialogWrapper(private val project: Project, type: FilterType) : DialogWrapper(project) {
    private var originalName: String? = null
    private val dialog = when (type) {
        FilterType.Tag -> TagFilterDialog()
        FilterType.CloudFormation -> CloudFormationFilterDialog(project)
    }

    init {
        init()
        title = message("explorer.filter.edit.title")
    }

    override fun doOKAction() {
        val (newName, value) = dialog.save()
        if (newName != originalName) {
            ResourceFilterManager.getInstance(project).state.remove(originalName)
        }
        ResourceFilterManager.getInstance(project).state[newName] = value
        project.redrawAwsTree()
        super.doOKAction()
    }

    fun load(name: String, filter: ResourceFilter) {
        originalName = name
        dialog.load(name, filter)
    }

    override fun doValidate(): ValidationInfo? {
        return dialog.validate()
    }

    override fun createCenterPanel(): JComponent? = dialog.component

    enum class FilterType {
        Tag,
        CloudFormation
    }
}

interface FilterDialog {
    val component: JComponent
    fun validate(): ValidationInfo?
    fun load(name: String, filter: ResourceFilter)
    fun save(): Pair<String, ResourceFilter>
}
