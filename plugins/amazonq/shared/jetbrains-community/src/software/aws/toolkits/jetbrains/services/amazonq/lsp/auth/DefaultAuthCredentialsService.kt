// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.BearerCredentials
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayloadData
import java.util.concurrent.CompletableFuture

class DefaultAuthCredentialsService(
    private val project: Project,
    private val encryptionManager: JwtEncryptionManager,
    serverInstance: Disposable,
) : AuthCredentialsService,
    BearerTokenProviderListener {
    init {
        project.messageBus.connect(serverInstance).subscribe(BearerTokenProviderListener.TOPIC, this)
    }

    override fun onChange(providerId: String, newScopes: List<String>?) {
        val connection = ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance())
            ?: return

        val provider = (connection.getConnectionSettings() as? TokenConnectionSettings)
            ?.tokenProvider
            ?.delegate as? BearerTokenProvider
            ?: return

        provider.currentToken()?.accessToken?.let { token ->
            updateTokenCredentials(token, false)
        }
    }

    override fun invalidate(providerId: String) {
        deleteTokenCredentials()
    }

    override fun updateTokenCredentials(accessToken: String, encrypted: Boolean): CompletableFuture<ResponseMessage> {
        val token = if (encrypted) {
            encryptionManager.decrypt(accessToken)
        } else {
            accessToken
        }

        val payload = createUpdateCredentialsPayload(token)

        return AmazonQLspService.executeIfRunning(project) { server ->
            server.updateTokenCredentials(payload)
        } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))
    }

    override fun deleteTokenCredentials(): CompletableFuture<Unit> =
        CompletableFuture<Unit>().also { completableFuture ->
            AmazonQLspService.executeIfRunning(project) { server ->
                server.deleteTokenCredentials()
                completableFuture.complete(null)
            } ?: completableFuture.completeExceptionally(IllegalStateException("LSP Server not running"))
        }

    private fun createUpdateCredentialsPayload(token: String): UpdateCredentialsPayload =
        UpdateCredentialsPayload(
            data = encryptionManager.encrypt(
                UpdateCredentialsPayloadData(
                    BearerCredentials(token)
                )
            ),
            encrypted = true
        )
}
