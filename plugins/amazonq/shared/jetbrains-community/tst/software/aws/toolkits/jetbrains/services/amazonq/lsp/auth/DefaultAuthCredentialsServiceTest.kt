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
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import java.time.Instant
import java.util.concurrent.CompletableFuture

class DefaultAuthCredentialsServiceTest {
    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    private lateinit var project: Project
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockEncryptionManager: JwtEncryptionManager
    private lateinit var sut: DefaultAuthCredentialsService

    // maybe better to use real project via junit extension
    @BeforeEach
    fun setUp() {
        project = spyk(projectExtension.project)
        mockLanguageServer = mockk<AmazonQLanguageServer>()
        mockEncryptionManager = mockk<JwtEncryptionManager>()
        every { mockEncryptionManager.encrypt(any()) } returns "mock-encrypted-data"

        // Mock the service methods on Project
        val mockLspService = mockk<AmazonQLspService>()
        every { project.getService(AmazonQLspService::class.java) } returns mockLspService
        every { project.serviceIfCreated<AmazonQLspService>() } returns mockLspService

        // Mock the LSP service's executeSync method as a suspend function
        every {
            mockLspService.executeSync<CompletableFuture<ResponseMessage>>(any())
        } coAnswers {
            val func = firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLspService, mockLanguageServer)
        }

        // Mock message bus
        val messageBus = mockk<MessageBus>()
        every { project.messageBus } returns messageBus
        val mockConnection = mockk<MessageBusConnection>()
        every { messageBus.connect(any<Disposable>()) } returns mockConnection
        every { mockConnection.subscribe(any(), any()) } just runs

        // Mock ToolkitConnectionManager
        val connectionManager = mockk<ToolkitConnectionManager>()
        val connection = mockk<AwsBearerTokenConnection>()
        val connectionSettings = mockk<TokenConnectionSettings>()
        val provider = mockk<ToolkitBearerTokenProvider>()
        val tokenDelegate = mockk<InteractiveBearerTokenProvider>()
        val token = PKCEAuthorizationGrantToken(
            issuerUrl = "https://example.com",
            refreshToken = "refreshToken",
            accessToken = "accessToken",
            expiresAt = Instant.MAX,
            createdAt = Instant.now(),
            region = "us-fake-1",
        )

        every { project.service<ToolkitConnectionManager>() } returns connectionManager
        every { connectionManager.activeConnectionForFeature(any()) } returns connection
        every { connection.getConnectionSettings() } returns connectionSettings
        every { connectionSettings.tokenProvider } returns provider
        every { provider.delegate } returns tokenDelegate
        every { tokenDelegate.currentToken() } returns token

        every {
            mockLanguageServer.updateTokenCredentials(any())
        } returns CompletableFuture.completedFuture(ResponseMessage())

        sut = DefaultAuthCredentialsService(project, this.mockEncryptionManager, mockk())
    }

    @Test
    fun `test updateTokenCredentials unencrypted success`() {
        val token = "unencryptedToken"
        val isEncrypted = false

        sut.updateTokenCredentials(token, isEncrypted)

        verify(exactly = 1) {
            mockLanguageServer.updateTokenCredentials(
                UpdateCredentialsPayload(
                    token,
                    isEncrypted
                )
            )
        }
    }

    @Test
    fun `test updateTokenCredentials encrypted success`() {
        val encryptedToken = "encryptedToken"
        val decryptedToken = "decryptedToken"
        val isEncrypted = true

        every { mockEncryptionManager.encrypt(any()) } returns encryptedToken

        sut.updateTokenCredentials(decryptedToken, isEncrypted)

        verify(atLeast = 1) {
            mockLanguageServer.updateTokenCredentials(
                UpdateCredentialsPayload(
                    encryptedToken,
                    isEncrypted
                )
            )
        }
    }

    @Test
    fun `test deleteTokenCredentials success`() {
        every { mockLanguageServer.deleteTokenCredentials() } returns CompletableFuture.completedFuture(Unit)

        sut.deleteTokenCredentials()

        verify(exactly = 1) { mockLanguageServer.deleteTokenCredentials() }
    }

    @Test
    fun `init results in token update`() {
        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }
}
