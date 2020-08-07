// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

class FilterDialogWrapper(project: Project) : DialogWrapper(project) {
    private val table = FilterDialog(project)

    init {
        init()
    }

    override fun doOKAction() {
        table.saveState()
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent? = table.panel
}

