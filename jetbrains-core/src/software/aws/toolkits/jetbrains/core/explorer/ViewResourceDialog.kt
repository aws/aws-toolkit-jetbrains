// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.panel
import javax.swing.JComponent

class ViewResourceDialog(project: Project, val resourceType: String) : DialogWrapper(project) {
    var resourceName = ""
    private val component by lazy {
        panel {
            row("$resourceType Name/URI:") {
                textField(::resourceName).constraints(grow)
            }
        }
    }

    init {
        super.init()
    }

    override fun createCenterPanel(): JComponent? = component
}
