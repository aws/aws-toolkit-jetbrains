// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import software.aws.toolkits.jetbrains.core.explorer.redrawAwsTree
import javax.swing.JComponent

class FilterDialogWrapper(private val project: Project, private val type: FilterType) : DialogWrapper(project) {
    private val dialog = when (type) {
        FilterType.Tag -> TagFilterDialog(project)
        FilterType.CloudFormation -> CloudFormationFilterDialog(project)
    }

    init {
        init()
        title = "TODO localize add/edit filter"
    }

    override fun doOKAction() {
        dialog.save()
        project.redrawAwsTree()
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent? = dialog.component

    // TODO get rid of
    enum class FilterType {
        Tag,
        CloudFormation
    }
}

interface FilterDialog {
    val component: JComponent
    fun validate()
    fun save()
}
