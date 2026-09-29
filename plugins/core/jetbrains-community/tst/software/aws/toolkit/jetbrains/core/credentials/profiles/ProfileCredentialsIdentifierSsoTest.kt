// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import migration.software.aws.toolkit.core.ToolkitClientManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.aws.toolkit.core.TokenConnectionSettings
import software.aws.toolkit.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkit.core.utils.test.aString
import software.aws.toolkit.jetbrains.core.MockClientManager
import software.aws.toolkit.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import software.aws.toolkit.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkit.jetbrains.core.credentials.sso.DiskCache
import software.aws.toolkit.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkit.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkit.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkit.jetbrains.core.credentials.sso.bearer.NoTokenInitializedException
import software.aws.toolkit.jetbrains.core.region.US_EAST_1
import software.aws.toolkit.jetbrains.utils.extensions.ApplicationExtension

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
        ApplicationManager.getApplication().replaceService(ToolkitClientManager::class.java, mockClientManager, disposable)

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

    @Test
    fun `cancelling the sso-session login does not throw`(@TestDisposable disposable: Disposable) {
        val session = ProfileSsoSessionIdentifier(aString(), aString(), US_EAST_1.id, setOf(aString()))
        val tokenProvider = mock<BearerTokenProvider> {
            on { state() } doReturn BearerTokenAuthState.NOT_AUTHENTICATED
            on { reauthenticate() } doThrow ProcessCanceledException()
        }
        val connectionSettings = TokenConnectionSettings(ToolkitBearerTokenProvider(tokenProvider), US_EAST_1)
        val connection = mock<AwsBearerTokenConnection> {
            on { startUrl } doReturn session.startUrl
            on { getConnectionSettings() } doReturn connectionSettings
        }
        val credentialManager = mock<CredentialManager> {
            on { getSsoSessionIdentifiers() } doReturn listOf(session)
        }
        val authManager = mock<ToolkitAuthManager> {
            on { getOrCreateSsoConnection(any()) } doReturn connection
        }
        ApplicationManager.getApplication().replaceService(CredentialManager::class.java, credentialManager, disposable)
        ApplicationManager.getApplication().replaceService(ToolkitAuthManager::class.java, authManager, disposable)

        val login = ProfileCredentialsIdentifierSso(aString(), session.profileName, null, null)
            .handleValidationException(IllegalStateException())
            ?.actions
            .orEmpty()
            .single()

        assertThatCode { runInEdtAndWait { login.actionPerformed(TestActionEvent()) } }.doesNotThrowAnyException()
    }
}
