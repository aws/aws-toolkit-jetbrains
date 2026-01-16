// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.utils.extensions

import com.intellij.testFramework.DisposableRule
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import software.aws.toolkit.jetbrains.core.MockClientManager
import software.aws.toolkit.jetbrains.core.credentials.sso.MockSsoLoginCallbackProvider
import software.aws.toolkit.jetbrains.core.credentials.sso.TestSsoPrompt

class SsoLoginExtension : DisposableRule(), BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val ssoSecret = getAnnotation(context) ?: return

        MockClientManager.Companion.useRealImplementations(disposable)
        MockSsoLoginCallbackProvider.Companion.getInstance().provider = TestSsoPrompt(ssoSecret)
    }

    override fun afterEach(context: ExtensionContext?) {
        MockSsoLoginCallbackProvider.Companion.getInstance().provider = null

        // todo: is there a better way to do this
        after()
    }

    private fun getAnnotation(context: ExtensionContext?): String? {
        if (context == null || context == context.root) {
            return null
        }

        return context.element.orElse(null)
            ?.annotations
            ?.filterIsInstance<SsoLogin>()
            ?.firstOrNull()
            ?.secretName
            ?: getAnnotation(context.parent.orElse(null))
    }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(SsoLoginExtension::class)
annotation class SsoLogin(val secretName: String)
