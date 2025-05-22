// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.BearerCredentials
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayloadData
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DefaultAuthCredentialsService(
    private val project: Project,
    private val encryptionManager: JwtEncryptionManager,
) : AuthCredentialsService,
    BearerTokenProviderListener,
    ToolkitConnectionManagerListener,
    QRegionProfileSelectedListener,
    Disposable {

    private val scheduler: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService()
    private var tokenSyncTask: ScheduledFuture<*>? = null
    private val tokenSyncIntervalMinutes = 5L

    init {
        project.messageBus.connect(this).apply {
            subscribe(BearerTokenProviderListener.TOPIC, this@DefaultAuthCredentialsService)
            subscribe(ToolkitConnectionManagerListener.TOPIC, this@DefaultAuthCredentialsService)
            subscribe(QRegionProfileSelectedListener.TOPIC, this@DefaultAuthCredentialsService)
        }

        if (isQConnected(project) && !isQExpired(project)) {
            updateTokenFromActiveConnection()
                .thenRun {
                    updateConfiguration()
                }
        }

        // Start periodic token sync
        startPeriodicTokenSync()
    }

    private fun startPeriodicTokenSync() {
        tokenSyncTask = scheduler.scheduleWithFixedDelay(
            {
                try {
                    if (isQConnected(project)) {
                        if (isQExpired(project)) {
                            val manager = ToolkitConnectionManager.getInstance(project)
                            val connection = manager.activeConnectionForFeature(QConnection.getInstance()) ?: return@scheduleWithFixedDelay

                            // Try to refresh the token if it's in NEEDS_REFRESH state
                            val tokenProvider = (connection.getConnectionSettings() as? TokenConnectionSettings)
                                ?.tokenProvider
                                ?.delegate
                                ?.let { it as? BearerTokenProvider } ?: return@scheduleWithFixedDelay

                            if (tokenProvider.state() == BearerTokenAuthState.NEEDS_REFRESH) {
                                try {
                                    tokenProvider.resolveToken()
                                    // Now that the token is refreshed, update it in Flare
                                    updateTokenFromActiveConnection()
                                } catch (e: Exception) {
                                    LOG.warn(e) { "Failed to refresh bearer token" }
                                }
                            }
                        } else {
                            updateTokenFromActiveConnection()
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn(e) { "Failed to sync bearer token to Flare" }
                }
            },
            tokenSyncIntervalMinutes,
            tokenSyncIntervalMinutes,
            TimeUnit.MINUTES
        )
    }

    override fun updateTokenCredentials(connection: ToolkitConnection, encrypted: Boolean): CompletableFuture<ResponseMessage> {
        val payload = try {
            createUpdateCredentialsPayload(connection, encrypted)
        } catch (e: Exception) {
            return CompletableFuture.failedFuture(e)
        }

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

    private fun updateTokenFromActiveConnection(): CompletableFuture<ResponseMessage> {
        val connection = ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance())
            ?: return CompletableFuture.failedFuture(IllegalStateException("No active Q connection"))

        return updateTokenFromConnection(connection)
    }

    private fun updateTokenFromConnection(connection: ToolkitConnection): CompletableFuture<ResponseMessage> =
        updateTokenCredentials(connection, true)

    override fun invalidate(providerId: String) {
        deleteTokenCredentials()
    }

    private fun createUpdateCredentialsPayload(connection: ToolkitConnection, encrypted: Boolean): UpdateCredentialsPayload {
        val token = (connection.getConnectionSettings() as? TokenConnectionSettings)
            ?.tokenProvider
            ?.delegate
            ?.let { it as? BearerTokenProvider }
            ?.currentToken()
            ?.accessToken
            ?: error("Unable to get token from connection")

        return if (encrypted) {
            UpdateCredentialsPayload(
                data = encryptionManager.encrypt(
                    UpdateCredentialsPayloadData(
                        BearerCredentials(token)
                    )
                ),
                metadata = ConnectionMetadata.fromConnection(connection),
                encrypted = true
            )
        } else {
            UpdateCredentialsPayload(
                data = token,
                metadata = ConnectionMetadata.fromConnection(connection),
                encrypted = false
            )
        }
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

    override fun dispose() {
        tokenSyncTask?.cancel(false)
        tokenSyncTask = null
    }

    companion object {
        private val LOG = getLogger<DefaultAuthCredentialsService>()
    }
}
