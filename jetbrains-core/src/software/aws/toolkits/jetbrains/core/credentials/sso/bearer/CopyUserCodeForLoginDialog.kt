// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso.bearer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel

import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.IconUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialType
import software.aws.toolkits.telemetry.Result
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.SwingConstants

class CopyUserCodeForLoginDialog(project: Project, authCode: String, private val title2: String = "Sign in with AWS Builder ID", private val credentialType: CredentialType): DialogWrapper(project) {

    private val pane =  panel {
        row {
            text("To proceed, open the login page and provide this code to confirm the access request from AWS Toolkit:", maxLineLength = -1)
        }

        row {
            cell(
                BorderLayoutPanel(5, 0).apply {
                    val action = CopyUserCodeForLogin(authCode)
                    addToCenter(JBLabel(authCode).setCopyable(true))
                    addToRight(ActionButton(action, action.templatePresentation.clone(), ActionPlaces.UNKNOWN, ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE))
                }
            ).horizontalAlign(HorizontalAlign.CENTER)
        }
    }
    override fun createCenterPanel(): JComponent? = pane

    init {
        title = title2
        super.init()
    }

    override fun doCancelAction() {
        super.doCancelAction()
        //AwsTelemetry.loginWithBrowser(project = null, Result.Cancelled, credentialType)
    }

}

class CopyUserCodeForLogin(private val authCode: String) : AnAction("Copy Code", "", AllIcons.Actions.Copy) {
    override fun actionPerformed(e: AnActionEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(authCode))
    }

}
