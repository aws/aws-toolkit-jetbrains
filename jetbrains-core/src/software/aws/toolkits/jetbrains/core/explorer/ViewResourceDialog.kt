// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

class ViewResourceDialog(project: Project, private val resourceType: String, private val resourceName: String): DialogWrapper(project)  {
    override fun createCenterPanel(): JComponent? {
        TODO("Not yet implemented")
    }
}
