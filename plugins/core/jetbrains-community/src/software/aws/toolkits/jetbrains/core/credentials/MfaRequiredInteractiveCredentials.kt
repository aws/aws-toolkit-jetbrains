// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.actionSystem.AnAction
import software.aws.toolkits.resources.AwsCoreBundle

interface MfaRequiredInteractiveCredentials : InteractiveCredential {
    override val userActionDisplayMessage: String get() = AwsCoreBundle.message("credentials.mfa.display", displayName)
    override val userActionShortDisplayMessage: String get() = AwsCoreBundle.message("credentials.mfa.display.short")

    override val userAction: AnAction get() = RefreshConnectionAction(AwsCoreBundle.message("credentials.mfa.action"))

    override fun userActionRequired(): Boolean = true
}
