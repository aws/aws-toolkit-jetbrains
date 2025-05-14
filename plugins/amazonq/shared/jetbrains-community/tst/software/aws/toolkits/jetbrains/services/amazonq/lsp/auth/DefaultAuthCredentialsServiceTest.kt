// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectExtension
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.sso.PKCEAuthorizationGrantToken
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
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
        every {
            mockLspService.executeSync<CompletableFuture<ResponseMessage>>(any())
        } coAnswers {
            val func = firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLspService, mockLanguageServer)
        }

        every {
            mockLanguageServer.updateTokenCredentials(any())
        } returns CompletableFuture<ResponseMessage>()

        every {
            mockLanguageServer.deleteTokenCredentials()
        } returns CompletableFuture.completedFuture(Unit)

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
        }
        every { project.service<ToolkitConnectionManager>() } returns mockConnectionManager
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
    fun `activeConnectionChanged updates token when connection ID matches Q connection`() {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())
        val newConnection = createMockConnection("new-token", "connection-id")
        every { mockConnection.id } returns "connection-id"

        sut.activeConnectionChanged(newConnection)

        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `activeConnectionChanged does not update token when connection ID differs`() {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())
        val newConnection = createMockConnection("new-token", "different-id")
        every { mockConnection.id } returns "q-connection-id"

        sut.activeConnectionChanged(newConnection)

        verify(exactly = 0) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `onChange updates token with new connection`() {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())
        setupMockConnectionManager("updated-token")

        sut.onChange("providerId", listOf("new-scope"))

        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `init does not update token when Q is not connected`() {
        every { isQConnected(project) } returns false
        every { isQExpired(project) } returns false

        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())

        verify(exactly = 0) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `init does not update token when Q is expired`() {
        every { isQConnected(project) } returns true
        every { isQExpired(project) } returns true

        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())

        verify(exactly = 0) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `test updateTokenCredentials unencrypted success`() {
        val isEncrypted = false
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())

        sut.updateTokenCredentials(mockConnection, isEncrypted)

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
    fun `test updateTokenCredentials encrypted success`() {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())

        val encryptedToken = "encryptedToken"
        val decryptedToken = "decryptedToken"
        val isEncrypted = true

        every { mockEncryptionManager.encrypt(any()) } returns encryptedToken

        sut.updateTokenCredentials(mockConnection, isEncrypted)

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
    fun `test deleteTokenCredentials success`() {
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())

        every { mockLanguageServer.deleteTokenCredentials() } returns CompletableFuture.completedFuture(Unit)

        sut.deleteTokenCredentials()

        verify(exactly = 1) { mockLanguageServer.deleteTokenCredentials() }
    }

    @Test
    fun `init results in token update`() {
        every { isQConnected(any()) } returns true
        every { isQExpired(any()) } returns false
        sut = DefaultAuthCredentialsService(project, mockEncryptionManager, mockk())

        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }
}
