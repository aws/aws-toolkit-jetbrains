// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import software.aws.toolkits.jetbrains.core.explorer.redrawAwsTree
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class FilterDialog(private val project: Project, type: FilterType) : DialogWrapper(project) {
    private var originalName: String? = null
    internal val content = when (type) {
        FilterType.Tag -> TagFilterDialog()
        FilterType.CloudFormation -> CloudFormationFilterDialog(project)
    }

    init {
        init()
        title = message("explorer.filter.edit.title")
    }

    override fun doOKAction() {
        save()
        super.doOKAction()
    }

    internal fun save() {
        val (newName, value) = content.save()
        if (newName != originalName) {
            ResourceFilterManager.getInstance(project).state.remove(originalName)
        }
        ResourceFilterManager.getInstance(project).state[newName] = value
        project.redrawAwsTree()
    }

    fun load(name: String, filter: ResourceFilter) {
        originalName = name
        content.load(name, filter)
    }

    override fun doValidate(): ValidationInfo? = content.validate()

    override fun createCenterPanel(): JComponent? = content.component

    enum class FilterType {
        Tag,
        CloudFormation
    }
}

interface FilterDialogPanel {
    val component: JComponent
    fun validate(): ValidationInfo?
    fun load(name: String, filter: ResourceFilter)
    fun save(): Pair<String, ResourceFilter>
}
