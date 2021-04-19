// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.resources.message
import javax.swing.Action
import javax.swing.JComponent

class SsmPluginInstallationWarning(project: Project): DialogWrapper(project) {
    private val warningIcon = JBLabel(Messages.getWarningIcon())
    private val component by lazy {
        panel {
            row {
                warningIcon(grow)
                right { label("Please install the AWS CLI before proceeding") }
                //right { label("Are you sure you want to continue debugging the container?") }

            }
            /*row{
                label("(Please ensure you have the required permissions before proceeding)")
            }*/
        }
    }

    override fun createCenterPanel(): JComponent? = component
    init {
        super.init()
        //title = "Install SSM Plugin"
        //title = "Enable Container Access"
        title = "Install AWS CLI"
    }


    override fun getHelpId(): String? = HelpIds.SESSION_MANAGER_INSTALLATION_INSTRUCTIONS.id


}
