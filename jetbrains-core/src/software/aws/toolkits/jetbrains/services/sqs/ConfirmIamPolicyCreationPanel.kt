// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import software.aws.toolkits.resources.message
import javax.swing.JPanel

class ConfirmIamPolicyCreationPanel {
    lateinit var component: JPanel
    lateinit var policyDocument: EditorTextField
    lateinit var warningText: JBLabel

    init {
        warningText.text = message("sqs.iam.warning.text")
    }
}
