// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import software.aws.toolkits.resources.message
import javax.swing.Action
import javax.swing.JComponent

class DisableCloudDebugWarning(project: Project) : DialogWrapper(project) {
    private val warningIcon = JBLabel(Messages.getWarningIcon())
    private val warningMessage = JBLabel(message("ecs.execute_command_disable_cloud_debug"))
    private val component by lazy {
        panel {
            row {
                warningIcon(grow)
                right {
                    warningMessage(grow)
                }
            }
        }
    }

    init {
        super.init()
        title = message("ecs.execute_command_disable_cloud_debug_title")
    }

    // Overriden to remove the Cancel button
    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent? = component
}
