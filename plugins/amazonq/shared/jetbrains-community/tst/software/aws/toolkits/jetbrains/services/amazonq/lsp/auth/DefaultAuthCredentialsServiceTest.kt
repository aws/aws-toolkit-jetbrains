// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectExtension
import com.intellij.testFramework.replaceService
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.sso.PKCEAuthorizationGrantToken
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import java.time.Instant
import java.util.concurrent.CompletableFuture

class DefaultAuthCredentialsServiceTest {
    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()

        private const val TEST_ACCESS_TOKEN = "test-access-token"
    }

    private lateinit var project: Project
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockEncryptionManager: JwtEncryptionManager
    private lateinit var mockConnectionManager: ToolkitConnectionManager
    private lateinit var mockConnection: AwsBearerTokenConnection
    private lateinit var sut: DefaultAuthCredentialsService

    @BeforeEach
    fun setUp() {
        project = spyk(projectExtension.project)
        setupMockLspService()
        setupMockMessageBus()
        setupMockConnectionManager()
    }

    private fun setupMockLspService() {
        mockLanguageServer = mockk<AmazonQLanguageServer>()
        mockEncryptionManager = mockk {
            every { encrypt(any()) } returns "mock-encrypted-data"
        }

        val mockLspService = mockk<AmazonQLspService>()
        coEvery {
            mockLspService.executeIfRunning<CompletableFuture<ResponseMessage>>(any())
        } coAnswers {
            val func = firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLspService, mockLanguageServer)
        }

        every {
            mockLanguageServer.updateTokenCredentials(any())
        } returns CompletableFuture.completedFuture(ResponseMessage())

        every {
            mockLanguageServer.deleteTokenCredentials()
        } returns Unit

        every {
            mockLanguageServer.updateConfiguration(any())
        } returns CompletableFuture.completedFuture(LspServerConfigurations(emptyList()))

        every { project.getService(AmazonQLspService::class.java) } returns mockLspService
        every { project.serviceIfCreated<AmazonQLspService>() } returns mockLspService
    }

    private fun setupMockMessageBus() {
        val messageBus = mockk<MessageBus>()
        val mockConnection = mockk<MessageBusConnection> {
            every { subscribe(any(), any()) } just runs
        }
        every { project.messageBus } returns messageBus
        every { messageBus.connect(any<Disposable>()) } returns mockConnection
    }

    private fun setupMockConnectionManager(accessToken: String = TEST_ACCESS_TOKEN) {
        mockConnection = createMockConnection(accessToken)
        mockConnectionManager = mockk {
            every { activeConnectionForFeature(any()) } returns mockConnection
            every { connectionStateForFeature(any()) } returns BearerTokenAuthState.AUTHORIZED
        }
        project.replaceService(ToolkitConnectionManager::class.java, mockConnectionManager, project)
        mockkStatic("software.aws.toolkits.jetbrains.utils.FunctionUtilsKt")
        // these set so init doesn't always emit
        every { isQConnected(any()) } returns false
        every { isQExpired(any()) } returns true
    }

    private fun createMockConnection(
        accessToken: String,
        connectionId: String = "test-connection-id",
    ): AwsBearerTokenConnection = mockk {
        every { id } returns connectionId
        every { startUrl } returns "startUrl"
        every { getConnectionSettings() } returns createMockTokenSettings(accessToken)
    }

    private fun createMockTokenSettings(accessToken: String): TokenConnectionSettings {
        val token = PKCEAuthorizationGrantToken(
            issuerUrl = "https://example.com",
            refreshToken = "refreshToken",
            accessToken = accessToken,
            expiresAt = Instant.MAX,
            createdAt = Instant.now(),
            region = "us-fake-1",
        )

        val tokenDelegate = mockk<InteractiveBearerTokenProvider> {
            every { currentToken() } returns token
        }

        val provider = mockk<ToolkitBearerTokenProvider> {
            every { delegate } returns tokenDelegate
        }

        return mockk {
            every { tokenProvider } returns provider
        }
    }

    @Test
    fun `activeConnectionChanged updates token when connection ID matches Q connection`() = runTest {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)
        val newConnection = createMockConnection("new-token", "connection-id")
        every { mockConnection.id } returns "connection-id"

        sut.activeConnectionChanged(newConnection)

        advanceUntilIdle()
        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `activeConnectionChanged does not update token when connection ID differs`() = runTest {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)
        val newConnection = createMockConnection("new-token", "different-id")
        every { mockConnection.id } returns "q-connection-id"

        sut.activeConnectionChanged(newConnection)

        advanceUntilIdle()
        verify(exactly = 0) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `onChange updates token with new connection`() = runTest {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)
        setupMockConnectionManager("updated-token")

        sut.onProviderChange("providerId", listOf("new-scope"))

        advanceUntilIdle()
        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `init does not update token when Q is not connected`() = runTest {
        every { isQConnected(project) } returns false
        every { isQExpired(project) } returns false

        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)

        advanceUntilIdle()
        verify(exactly = 0) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `init does not update token when Q is expired`() = runTest {
        every { isQConnected(project) } returns true
        every { isQExpired(project) } returns true

        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)

        advanceUntilIdle()
        verify(exactly = 0) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `test updateTokenCredentials unencrypted success`() = runTest {
        val isEncrypted = false
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)

        sut.updateTokenCredentials(mockConnection, isEncrypted)

        advanceUntilIdle()
        verify(exactly = 1) {
            mockLanguageServer.updateTokenCredentials(
                UpdateCredentialsPayload(
                    "test-access-token",
                    ConnectionMetadata(
                        SsoProfileData("startUrl")
                    ),
                    isEncrypted
                )
            )
        }
    }

    @Test
    fun `test updateTokenCredentials encrypted success`() = runTest {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)

        val encryptedToken = "encryptedToken"
        val isEncrypted = true

        every { mockEncryptionManager.encrypt(any()) } returns encryptedToken

        sut.updateTokenCredentials(mockConnection, isEncrypted)

        advanceUntilIdle()
        verify(atLeast = 1) {
            mockLanguageServer.updateTokenCredentials(
                UpdateCredentialsPayload(
                    encryptedToken,
                    ConnectionMetadata(
                        SsoProfileData("startUrl")
                    ),
                    isEncrypted
                )
            )
        }
    }

    @Test
    fun `test deleteTokenCredentials success`() = runTest {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)

        every { mockLanguageServer.deleteTokenCredentials() } returns Unit

        sut.deleteTokenCredentials()

        advanceUntilIdle()
        verify(exactly = 1) { mockLanguageServer.deleteTokenCredentials() }
    }

    @Test
    fun `init results in token update`() = runTest {
        every { isQConnected(any()) } returns true
        every { isQExpired(any()) } returns false
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, this)

        advanceUntilIdle()
        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }
}
