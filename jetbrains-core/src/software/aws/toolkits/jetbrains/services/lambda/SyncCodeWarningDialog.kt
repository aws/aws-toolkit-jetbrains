// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class SyncCodeWarningDialog(private val project: Project): DialogWrapper(project) {

    private val component by lazy {
        panel {
            row {
                label("The SAM CLI will use the AWS Lambda, Amazon API Gateway, and AWS StepFunctions APIs to upload your code without \n" +
                    "\n" +
                    "performing a CloudFormation deployment. This will cause drift in your CloudFormation stack. \n" +
                    "\n" +
                    "**The sync command should only be used against a development stack**.\n" +
                    "\n" +
                    "Confirm that you are synchronizing a development stack.")
            }
        }
    }

    init {
        super.init()
        title = "Confirm dev mode"
    }

    override fun createCenterPanel(): JComponent? = component
}
