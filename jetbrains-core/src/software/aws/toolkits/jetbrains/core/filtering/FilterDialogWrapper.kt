// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import javax.swing.JComponent

class FilterDialogWrapper(private val project: Project) : DialogWrapper(project) {
    private val table = FilterDialog(project)

    init {
        init()
        title = "TODO localize add/edit filter"
    }

    override fun doOKAction() {
        // TODO table.saveState()
        // TODO only refresh if something changes
        project.refreshAwsTree()
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent? = table.component
}
