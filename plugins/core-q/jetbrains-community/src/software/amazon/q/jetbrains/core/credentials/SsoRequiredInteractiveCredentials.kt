// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials

import com.intellij.openapi.actionSystem.AnAction
import software.amazon.q.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.amazon.q.jetbrains.core.credentials.sso.LazyAccessTokenProvider
import software.amazon.q.jetbrains.core.credentials.sso.SsoCache
import software.amazon.q.resources.AwsCoreBundle

interface SsoRequiredInteractiveCredentials : InteractiveCredential {
    val ssoCache: SsoCache
    val ssoUrl: String
    val ssoRegion: String

    override val userActionDisplayMessage: String get() = AwsCoreBundle.message("credentials.sso.display", displayName)
    override val userActionShortDisplayMessage: String get() = AwsCoreBundle.message("credentials.sso.display.short")

    override val userAction: AnAction get() = RefreshConnectionAction(AwsCoreBundle.message("credentials.sso.action"))

    private val lazyTokenProvider: LazyAccessTokenProvider
        get() = LazyAccessTokenProvider(
            ssoCache,
            ssoUrl,
            ssoRegion,
            listOf(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
        )

    // assumes single scope if we're going through this interface
    override fun userActionRequired(): Boolean = lazyTokenProvider.resolveToken() == null

    fun invalidateCurrentToken() = lazyTokenProvider.invalidate()
}
