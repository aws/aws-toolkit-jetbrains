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

class SessionManagerPluginWarning(project: Project) : DialogWrapper(project) {
    private val warningIcon = JBLabel(Messages.getWarningIcon())
    private val warningMessage = JBLabel(message("session_manager_plugin_installation_warning"))
    private val component by lazy {
        panel {
            row {
                warningIcon(grow)
                right {
                    warningMessage(grow).also { it.component.setCopyable(true) }
                }
            }
        }
    }

    init {
        super.init()
        title = message("session_manager_plugin_installation_warning_title")
    }

    override fun createCenterPanel(): JComponent? = component

    override fun createActions(): Array<Action> = arrayOf(okAction)
}
