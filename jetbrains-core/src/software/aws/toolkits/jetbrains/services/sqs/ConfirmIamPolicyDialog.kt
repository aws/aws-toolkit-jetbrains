// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.json.JsonLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import software.aws.toolkits.jetbrains.services.lambda.upload.SQS_POLLER_ROLE_POLICY
import software.aws.toolkits.jetbrains.utils.ui.formatAndSet
import java.awt.Component
import javax.swing.JComponent

class ConfirmIamPolicyDialog(
    private val project: Project,
    private val parent: Component? = null
) : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {
    val view = ConfirmIamPolicyCreationPanel()

    init {
        title = "Create IAM Role"
        setOKButtonText("Create")

        view.policyDocument.formatAndSet(SQS_POLLER_ROLE_POLICY, JsonLanguage.INSTANCE)
        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun doOKAction() {
        println("Created IAM Role policy")
        doOKAction()
    }
}
