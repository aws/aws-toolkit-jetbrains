// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import software.aws.toolkits.jetbrains.settings.SamDisplayDevModeWarningSettings
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class SyncCodeWarningDialog(private val project: Project): DialogWrapper(project) {
    private val settings = SamDisplayDevModeWarningSettings.getInstance()
    var dontDisplayWarning = false
    private val component by lazy {
        panel {
            row {
                label("The SAM CLI will use the AWS Lambda, Amazon API Gateway, and AWS StepFunctions APIs to upload your code without" +
                    "\n" +
                    "performing a CloudFormation deployment. This will cause drift in your CloudFormation stack." +
                    "\n" +
                    "**The sync command should only be used against a development stack**." +
                    "\n" +
                    "Confirm that you are synchronizing a development stack.")
            }
            row {
                checkBox(message("general.notification.action.hide_forever")).bindSelected({ dontDisplayWarning }, { dontDisplayWarning = it })
            }
        }
    }

    init {
        super.init()
        title = "Confirm dev mode"
        setOKButtonText("Confirm")
    }

    override fun createCenterPanel(): JComponent? = component

    override fun doOKAction() {
        super.doOKAction()
        if(dontDisplayWarning) {
            settings.showDevModeWarning = false
        }
    }
}
