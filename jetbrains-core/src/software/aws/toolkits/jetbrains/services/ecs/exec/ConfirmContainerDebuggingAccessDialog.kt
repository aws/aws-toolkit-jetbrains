// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import javax.swing.JComponent

class ConfirmContainerDebuggingAccessDialog(
    project: Project,
    container: ContainerDetails
) : DialogWrapper(project) {
    private val component by lazy{
        panel{
            row{

            }
        }
    }
    init {
        super.init()
    }
    override fun createCenterPanel(): JComponent? = component

}
