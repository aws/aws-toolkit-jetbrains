// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import javax.swing.JComponent
import com.intellij.ui.layout.panel

class UploadFunctionContinueDialog(private val project: Project, private val changeSet: String) : DialogWrapper(project) {
    init {
        super.init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row { JBLabel("abc")() }
        row { JBLabel(changeSet)() }
    }
}
