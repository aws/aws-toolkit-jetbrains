// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.ProcessCanceledException
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.ConfirmUserCodeLoginDialog
import software.aws.toolkits.jetbrains.utils.computeOnEdt
import software.aws.toolkits.jetbrains.utils.isQWebviewsAvailable
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.AwsCoreBundle
import software.aws.toolkits.telemetry.AuthType
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialType
import software.aws.toolkits.telemetry.Result

typealias SsoLoginCallbackProvider = migration.software.aws.toolkits.jetbrains.core.credentials.sso.SsoLoginCallbackProvider

class DefaultSsoLoginCallbackProvider : SsoLoginCallbackProvider {
    override fun getProvider(isAlwaysShowDeviceCode: Boolean, ssoUrl: String): SsoLoginCallback {
        val deviceCodeProvider = if (ssoUrl == SONO_URL) {
            DefaultBearerTokenPrompt
        } else {
            DefaultSsoPrompt
        }

        if (isAlwaysShowDeviceCode) {
            return deviceCodeProvider
        }

        return when {
            isQWebviewsAvailable() -> SsoPromptWithBrowserSupport
            else -> deviceCodeProvider
        }
    }
}

interface SsoPrompt : SsoLoginCallback {
    override fun tokenRetrieved() {
        AwsTelemetry.loginWithBrowser(project = null, result = Result.Succeeded, credentialType = CredentialType.SsoProfile, authType = AuthType.DeviceCode)
    }

    override fun tokenRetrievalFailure(e: Exception) {
        e.notifyError(AwsCoreBundle.message("credentials.sso.login.failed"))
        AwsTelemetry.loginWithBrowser(project = null, result = Result.Failed, credentialType = CredentialType.SsoProfile, authType = AuthType.DeviceCode)
    }
}

object DefaultSsoPrompt : SsoPrompt {
    override fun tokenPending(authorization: Authorization) {
        computeOnEdt {
            val result = ConfirmUserCodeLoginDialog(
                authorization.userCode,
                AwsCoreBundle.message("credentials.sso.login.title"),
                CredentialType.SsoProfile
            ).showAndGet()

            if (result) {
                BrowserUtil.browse(authorization.verificationUriComplete)
            } else {
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    result = Result.Cancelled,
                    credentialType = CredentialType.SsoProfile,
                    authType = AuthType.DeviceCode
                )
                throw ProcessCanceledException(IllegalStateException(AwsCoreBundle.message("credentials.sso.login.cancelled")))
            }
        }
    }
}

object SsoPromptWithBrowserSupport : SsoPrompt {
    override fun tokenPending(authorization: Authorization) {
        computeOnEdt {
            BrowserUtil.browse(authorization.verificationUriComplete)
        }
    }
}

interface BearerTokenPrompt : SsoLoginCallback {
    override fun tokenRetrieved() {
        AwsTelemetry.loginWithBrowser(project = null, result = Result.Succeeded, credentialType = CredentialType.BearerToken, authType = AuthType.DeviceCode)
    }

    override fun tokenRetrievalFailure(e: Exception) {
        AwsTelemetry.loginWithBrowser(project = null, result = Result.Failed, credentialType = CredentialType.BearerToken, authType = AuthType.DeviceCode)
    }
}

object DefaultBearerTokenPrompt : BearerTokenPrompt {
    override fun tokenPending(authorization: Authorization) {
        computeOnEdt {
            val codeCopied = ConfirmUserCodeLoginDialog(
                authorization.userCode,
                AwsCoreBundle.message("credentials.sono.login"),
                CredentialType.BearerToken
            ).showAndGet()

            if (codeCopied) {
                BrowserUtil.browse(authorization.verificationUriComplete)
            } else {
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    result = Result.Cancelled,
                    credentialType = CredentialType.BearerToken,
                    authType = AuthType.DeviceCode
                )
            }
        }
    }
}
