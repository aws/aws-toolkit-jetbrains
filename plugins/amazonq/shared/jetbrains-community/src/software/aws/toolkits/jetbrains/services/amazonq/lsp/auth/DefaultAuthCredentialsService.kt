// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import software.amazon.q.core.TokenConnectionSettings
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.credentials.ToolkitConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.BearerCredentials
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayloadData
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.amazon.q.jetbrains.utils.isQConnected
import software.amazon.q.jetbrains.utils.isQExpired
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DefaultAuthCredentialsService(
    private val project: Project,
    private val encryptionManager: JwtEncryptionManager,
    private val cs: CoroutineScope,
) : BearerTokenProviderListener,
    ToolkitConnectionManagerListener,
    QRegionProfileSelectedListener,
    Disposable {

    private val scheduler: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService()
    private var tokenRefreshTask: ScheduledFuture<*>? = null
    private val tokenRefreshInterval = 5L

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

        // Start periodic token refresh
        startPeriodicTokenRefresh()
    }

    // TODO: we really only need a single application-wide instance of this
    private fun startPeriodicTokenRefresh() {
        tokenRefreshTask = scheduler.scheduleWithFixedDelay(
            {
                try {
                    if (isQConnected(project)) {
                        val manager = ToolkitConnectionManager.getInstance(project)
                        val connection = manager.activeConnectionForFeature(QConnection.getInstance()) ?: return@scheduleWithFixedDelay

                        // periodically poll token to trigger a background refresh if needed
                        val tokenProvider = (connection.getConnectionSettings() as? TokenConnectionSettings)
                            ?.tokenProvider
                            ?.delegate
                            ?.let { it as? BearerTokenProvider } ?: return@scheduleWithFixedDelay
                        tokenProvider.resolveToken()
                    }
                } catch (e: Exception) {
                    LOG.warn(e) { "Failed to refresh bearer token" }
                }
            },
            tokenRefreshInterval,
            tokenRefreshInterval,
            TimeUnit.MINUTES
        )
    }

    fun updateTokenCredentials(connection: ToolkitConnection, encrypted: Boolean): CompletableFuture<ResponseMessage> {
        val payload = try {
            createUpdateCredentialsPayload(connection, encrypted)
        } catch (e: Exception) {
            return CompletableFuture.failedFuture(e)
        }

        return cs.async {
            val result = AmazonQLspService.executeAsyncIfRunning(project) { server ->
                server.updateTokenCredentials(payload)
            } ?: CompletableFuture.failedFuture(IllegalStateException("LSP Server not running"))

            result.thenApply { response ->
                updateConfiguration()

                response
            }.await()
        }.asCompletableFuture()
    }

    fun deleteTokenCredentials() {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { server ->
                server.deleteTokenCredentials()
            }
        }
    }

    override fun onProviderChange(providerId: String, newScopes: List<String>?) {
        updateTokenFromActiveConnection()
    }

    override fun onTokenModified(providerId: String) {
        updateTokenFromActiveConnection()
    }

    override fun invalidate(providerId: String) {
        deleteTokenCredentials()
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

    private fun updateConfiguration() {
        cs.launch {
            val payload = UpdateConfigurationParams(
                section = "aws.q",
                settings = mapOf(
                    "profileArn" to QRegionProfileManager.getInstance().activeProfile(project)?.arn
                )
            )
            AmazonQLspService.executeAsyncIfRunning(project) { server ->
                server.updateConfiguration(payload)
            }
        }
    }

    override fun dispose() {
        tokenRefreshTask?.cancel(false)
        tokenRefreshTask = null
    }

    companion object {
        private val LOG = getLogger<DefaultAuthCredentialsService>()
    }
}
