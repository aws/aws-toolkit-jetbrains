// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.panel
import javax.swing.JComponent

class EnableExecuteCommandWarning(project: Project) : DialogWrapper(project) {
    private val component by lazy{
        panel{
            row{
                label("Enabling Execute Commands will change the state of resources in your AWS account, including but not limited to stopping and restarting the service")
            }
            row{
                label("Altering the state of resources while Execute Command is enabled can lead to unpredictable results.")
            }

        }
    }
    init {
        super.init()
    }
    override fun createCenterPanel(): JComponent = component

}
