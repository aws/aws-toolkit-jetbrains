// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.aws.toolkits.jetbrains.core.MockClientManager
import software.aws.toolkits.jetbrains.core.credentials.sso.DiskCache
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.NoTokenInitializedException

@ExtendWith(ApplicationExtension::class)
class ProfileCredentialsIdentifierSsoTest {
    private val sut = ProfileCredentialsIdentifierSso("", "", "", null)

//    @BeforeEach
//    fun setUp(@TestDisposable disposable: Disposable) {
//        CoreTestHelper.registerMissingServices(disposable)
//    }

    @Test
    fun `handles SsoOidcException`() {
        val exception = SsoOidcException.builder().message("message").build()

        assertThat(sut.handleValidationException(exception)).isNotNull()
    }

    @Test
    fun `handles nested SsoOidcException`() {
        val root = SsoOidcException.builder().message("message").build()
        // Exception(Exception(Exception(...)))
        val exception = (1..1000).fold(root as Exception) { acc, _ -> Exception(acc) }

        assertThat(sut.handleValidationException(exception)).isNotNull()
    }

    @Test
    fun `handles exception from uninitialized token provider`(@TestDisposable disposable: Disposable) {
        val mockClientManager = MockClientManager()
        ApplicationManager.getApplication().replaceService(migration.software.aws.toolkits.core.ToolkitClientManager::class.java, mockClientManager, disposable)

        val cache = mock<DiskCache>()
        mockClientManager.register(SsoOidcClient::class, mock<SsoOidcClient>())

        val exception = assertThrows<NoTokenInitializedException> {
            InteractiveBearerTokenProvider("", "us-east-1", listOf("scopes"), cache = cache, id = "test").resolveToken()
        }
        assertThat(sut.handleValidationException(exception)).isNotNull()
    }

    @Test
    fun `ignores arbitrary exception`() {
        assertThat(sut.handleValidationException(RuntimeException())).isNull()
    }
}
