// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.Before
import org.junit.Test
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import java.util.concurrent.CompletableFuture

class DefaultAuthCredentialsServiceTest {
    private lateinit var project: Project
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockEncryptionManager: JwtEncryptionManager
    private lateinit var sut: DefaultAuthCredentialsService

    @Before
    fun setUp() {
        project = mockk<Project>()
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
            val func = firstArg<suspend (AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLanguageServer)
        }

        sut = DefaultAuthCredentialsService(project, this.mockEncryptionManager)
    }

    @Test
    fun `test updateTokenCredentials unencrypted success`() {
        val token = "unencryptedToken"
        val isEncrypted = false

        every {
            mockLanguageServer.updateTokenCredentials(any())
        } returns CompletableFuture.completedFuture(ResponseMessage())

        sut.updateTokenCredentials(token, isEncrypted)

        verify(exactly = 0) {
            mockEncryptionManager.decrypt(any())
        }
        verify(exactly = 1) {
            mockLanguageServer.updateTokenCredentials(any())
        }
    }

    @Test
    fun `test updateTokenCredentials encrypted success`() {
        val encryptedToken = "encryptedToken"
        val decryptedToken = "decryptedToken"
        val isEncrypted = true

        every { mockEncryptionManager.decrypt(encryptedToken) } returns decryptedToken
        every { mockEncryptionManager.encrypt(any()) } returns "mock-encrypted-data"
        every {
            mockLanguageServer.updateTokenCredentials(any())
        } returns CompletableFuture.completedFuture(ResponseMessage())

        sut.updateTokenCredentials(encryptedToken, isEncrypted)

        verify(exactly = 1) { mockEncryptionManager.decrypt(encryptedToken) }
        verify(exactly = 1) { mockLanguageServer.updateTokenCredentials(any()) }
    }

    @Test
    fun `test deleteTokenCredentials success`() {
        every { mockLanguageServer.deleteTokenCredentials() } returns CompletableFuture.completedFuture(Unit)

        sut.deleteTokenCredentials()

        verify(exactly = 1) { mockLanguageServer.deleteTokenCredentials() }
    }
}
