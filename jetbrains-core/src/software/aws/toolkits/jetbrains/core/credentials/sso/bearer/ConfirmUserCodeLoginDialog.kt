// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso.bearer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.credentials.sono.CodeCatalystCredentialManager
import software.aws.toolkits.jetbrains.core.gettingstarted.requestCredentialsForCodeCatalyst
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialType
import software.aws.toolkits.telemetry.Result
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent

class ConfirmUserCodeLoginDialog(
    private val authCode: String,
    private val dialogTitle: String,
    private val credentialType: CredentialType
) : DialogWrapper(null) {

    private val pane = panel {
        row {
            label(message("aws.sso.signing.device.code.copy.dialog.text"))
        }

        row {
            cell(
                BorderLayoutPanel(5, 0).apply {
                    val action = CopyUserCodeForLogin(authCode)
                    addToCenter(
                        JBLabel(authCode).apply {
                            tryOrNull {
                                JBFont.create(JBFont.decode(EditorColorsUtil.getGlobalOrDefaultColorScheme().consoleFontName)).biggerOn(9f).asBold()
                            }?.let {
                                font = it
                            }
                            setCopyable(true)
                        }
                    )
                    addToRight(ActionButton(action, action.templatePresentation.clone(), ActionPlaces.UNKNOWN, ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE))
                }
            ).horizontalAlign(HorizontalAlign.CENTER)
        }
    }

    override fun createCenterPanel(): JComponent? = pane

    init {
        title = dialogTitle
        setOKButtonText(message("aws.sso.signing.device.code"))
        super.init()
    }

    override fun doCancelAction() {
        super.doCancelAction()
        AwsTelemetry.loginWithBrowser(project = null, Result.Cancelled, credentialType)
    }

    override fun createActions(): Array<Action> {
        val defaultButtons = arrayOf(cancelAction, okAction)
        return if (ApplicationInfo.getInstance().build.productCode != "GW") {
            defaultButtons
        } else {
            val activeConnection = CodeCatalystCredentialManager.getInstance(null).connection()
            if (activeConnection != null) {
                arrayOf(SignInWithDifferentAccountButton(), cancelAction, okAction)
            } else {
                defaultButtons
            }
        }
    }

    private inner class SignInWithDifferentAccountButton : DialogWrapperAction(message("aws.sso.signing.device.code.diff.account")) {
        override fun doAction(e: ActionEvent?) {
            close(CLOSE_EXIT_CODE)
            requestCredentialsForCodeCatalyst(project = null)
        }
    }
}

class CopyUserCodeForLogin(private val authCode: String) : AnAction(message("aws.sso.signing.device.code.copy"), "", AllIcons.Actions.Copy) {
    override fun actionPerformed(e: AnActionEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(authCode))
    }
}
