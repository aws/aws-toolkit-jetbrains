// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.ui.Messages
import software.aws.toolkits.jetbrains.core.credentials.sso.Authorization
import software.aws.toolkits.jetbrains.core.credentials.sso.DiskCache
import software.aws.toolkits.jetbrains.core.credentials.sso.SsoCache
import software.aws.toolkits.jetbrains.core.credentials.sso.SsoLoginCallback
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.CopyUserCodeForLoginDialog
import software.aws.toolkits.jetbrains.utils.computeOnEdt
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CredentialType

/**
 * Shared disk cache for SSO for the IDE
 */
val diskCache by lazy { DiskCache() }

object SsoPrompt : SsoLoginCallback {
    override fun tokenPending(authorization: Authorization) {
        computeOnEdt {
            val result = CopyUserCodeForLoginDialog(DefaultProjectFactory.getInstance().defaultProject, authorization.userCode, message("credentials.sso.login.title"), CredentialType.SsoProfile).showAndGet()
            /*Messages.showOkCancelDialog(
                message("credentials.sso.login.message", authorization.verificationUri, authorization.userCode),
                message("credentials.sso.login.title"),
                message("credentials.sso.login.open_browser"),
                Messages.getCancelButton(),
                null
            )*/

            if (result) {
                BrowserUtil.browse(authorization.verificationUri)
            } else {
                throw ProcessCanceledException(IllegalStateException(message("credentials.sso.login.cancelled")))
            }
        }
    }

    override fun tokenRetrieved() {}

    override fun tokenRetrievalFailure(e: Exception) {
        e.notifyError(message("credentials.sso.login.failed"))
    }
}

interface SsoRequiredInteractiveCredentials : InteractiveCredential {
    val ssoCache: SsoCache
    val ssoUrl: String

    override val userActionDisplayMessage: String get() = message("credentials.sso.display", displayName)
    override val userActionShortDisplayMessage: String get() = message("credentials.sso.display.short")

    override val userAction: AnAction get() = RefreshConnectionAction(message("credentials.sso.action"))

    override fun userActionRequired(): Boolean = ssoCache.loadAccessToken(ssoUrl) == null
}
