// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.BearerCredentials
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayloadData
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import java.util.concurrent.CompletableFuture

class DefaultAuthCredentialsService(
    private val project: Project,
    private val encryptionManager: JwtEncryptionManager,
    serverInstance: Disposable,
) : AuthCredentialsService,
    BearerTokenProviderListener,
    ToolkitConnectionManagerListener,
    QRegionProfileSelectedListener {

    init {
        project.messageBus.connect(serverInstance).apply {
            subscribe(BearerTokenProviderListener.TOPIC, this@DefaultAuthCredentialsService)
            subscribe(ToolkitConnectionManagerListener.TOPIC, this@DefaultAuthCredentialsService)
            subscribe(QRegionProfileSelectedListener.TOPIC, this@DefaultAuthCredentialsService)
        }

        if (isQConnected(project) && !isQExpired(project)) {
            updateTokenFromActiveConnection()
            updateConfiguration()
        }
    }

    override fun updateTokenCredentials(accessToken: String, encrypted: Boolean): CompletableFuture<ResponseMessage> {
        val payload = createUpdateCredentialsPayload(accessToken, encrypted)

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

    override fun onChange(providerId: String, newScopes: List<String>?) {
        updateTokenFromActiveConnection()
    }

    override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
        val qConnection = ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance())
            ?: return
        if (newConnection?.id != qConnection.id) return

        updateTokenFromConnection(newConnection)
    }

    private fun updateTokenFromActiveConnection() {
        val connection = ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance())
            ?: return

        updateTokenFromConnection(connection)
    }

    private fun updateTokenFromConnection(connection: ToolkitConnection) {
        (connection.getConnectionSettings() as? TokenConnectionSettings)
            ?.tokenProvider
            ?.delegate
            ?.let { it as? BearerTokenProvider }
            ?.currentToken()
            ?.accessToken
            ?.let { token -> updateTokenCredentials(token, true) }
    }

    override fun invalidate(providerId: String) {
        deleteTokenCredentials()
    }

    private fun createUpdateCredentialsPayload(token: String, encrypted: Boolean): UpdateCredentialsPayload =
        if (encrypted) {
            UpdateCredentialsPayload(
                data = encryptionManager.encrypt(
                    UpdateCredentialsPayloadData(
                        BearerCredentials(token)
                    )
                ),
                encrypted = true
            )
        } else {
            UpdateCredentialsPayload(
                data = token,
                encrypted = false
            )
        }

    override fun onProfileSelected(project: Project, profile: QRegionProfile?) {
        updateConfiguration()
    }

    private fun updateConfiguration(): CompletableFuture<LspServerConfigurations> {
        val payload = UpdateConfigurationParams(
            section = "aws.q",
            settings = mapOf(
                "profileArn" to QRegionProfileManager.getInstance().activeProfile(project)?.arn
            )
        )
        return AmazonQLspService.executeIfRunning(project) { server ->
            server.updateConfiguration(payload)
        } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))
    }
}
