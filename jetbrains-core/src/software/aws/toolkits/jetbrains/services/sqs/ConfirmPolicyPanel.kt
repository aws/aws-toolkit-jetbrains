// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.json.JsonLanguage
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.components.JBLabel
import javax.swing.JPanel

class ConfirmPolicyPanel(
    private val project: Project,
    warning: String
) {
    lateinit var component: JPanel
    lateinit var policyDocument: EditorTextField
    lateinit var warningText: JBLabel

    init {
        warningText.text = warning
    }

    private fun createUIComponents() {
        policyDocument = EditorTextFieldProvider.getInstance().getEditorField(JsonLanguage.INSTANCE, project, emptyList())
    }
}
