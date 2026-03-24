// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.DirectEncrypter
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkit.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkit.jetbrains.core.credentials.ConnectionState
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.jetbrains.settings.CfnLspSettingsChangeListener
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages AWS credentials for the CloudFormation LSP server.
 *
 * Credentials are JWE-encrypted (A256GCM) before being sent to the server.
 * The encryption key is generated per-session and passed in initialization options.
 */
@Service(Service.Level.PROJECT)
internal class CfnCredentialsService(private val project: Project) : Disposable {
    private val encryptionKey: SecretKey = generateKey()
    private var lastRegionId: String? = AwsConnectionManager.getInstance(project).selectedRegion?.id

    init {
        val appBus = ApplicationManager.getApplication().messageBus.connect(this)
        subscribeToCredentialChanges(appBus)
        subscribeToSettingsChanges(appBus)
        subscribeToServerStateChanges()
    }

    val encryptionKeyBase64: String
        get() = Base64.getEncoder().encodeToString(encryptionKey.encoded)

    fun sendCredentials(onRegionChange: Boolean = false) {
        val credentials = resolveCredentials() ?: return
        val encrypted = encrypt(credentials)
        CfnClientService.getInstance(project).updateIamCredentials(UpdateCredentialsParams(encrypted, true))
            .thenAccept { result ->
                LOG.info { "Credentials updated on LSP server: success=${result?.success}" }
                if (onRegionChange && result?.success == true) {
                    val stacksManager = StacksManager.getInstance(project)
                    stacksManager.clear()

                    val resourceLoader = ResourceLoader.getInstance(project)
                    resourceLoader.clear(null)

                    stacksManager.reload()
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to update credentials on LSP server" }
                null
            }
    }

    fun notifyConfigurationChanged() {
        CfnClientService.getInstance(project).notifyConfigurationChanged()
        LOG.info { "Sent didChangeConfiguration to LSP server" }
    }

    private fun subscribeToCredentialChanges(appBus: com.intellij.util.messages.MessageBusConnection) {
        appBus.subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    sendCredentials()
                }
            }
        )

        project.messageBus.connect(this).subscribe(
            AwsConnectionManager.CONNECTION_SETTINGS_STATE_CHANGED,
            object : ConnectionSettingsStateChangeNotifier {
                override fun settingsStateChanged(newState: ConnectionState) {
                    if (newState is ConnectionState.ValidConnection) {
                        val newRegionId = newState.region.id
                        val regionChanged = lastRegionId != null && lastRegionId != newRegionId
                        lastRegionId = newRegionId
                        sendCredentials(onRegionChange = regionChanged)
                    }
                }
            }
        )
    }

    private fun subscribeToSettingsChanges(appBus: com.intellij.util.messages.MessageBusConnection) {
        appBus.subscribe(
            CfnLspSettingsChangeListener.TOPIC,
            CfnLspSettingsChangeListener {
                notifyConfigurationChanged()
            }
        )
    }

    @Suppress("UnstableApiUsage")
    private fun subscribeToServerStateChanges() {
        LspServerManager.getInstance(project).addLspServerManagerListener(
            object : LspServerManagerListener {
                override fun serverStateChanged(lspServer: LspServer) {
                    if (lspServer.state == LspServerState.Running) {
                        LOG.info { "LSP server running, sending credentials" }
                        sendCredentials()
                    }
                }
            },
            this,
            true
        )
    }

    private fun resolveCredentials(): IamCredentials? {
        val connectionManager = AwsConnectionManager.getInstance(project)
        val region = connectionManager.selectedRegion ?: return null

        return try {
            val credentialProvider = connectionManager.activeCredentialProvider
            val awsCredentials = credentialProvider.resolveCredentials()
            val sessionCredentials = awsCredentials as? AwsSessionCredentials

            IamCredentials(
                profile = credentialProvider.shortName,
                region = region.id,
                accessKeyId = awsCredentials.accessKeyId(),
                secretAccessKey = awsCredentials.secretAccessKey(),
                sessionToken = sessionCredentials?.sessionToken()
            )
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to resolve credentials" }
            null
        }
    }

    private fun encrypt(credentials: IamCredentials): String {
        val payload = """{"data":${jacksonObjectMapper().writeValueAsString(credentials)}}"""
        val jwe = JWEObject(
            JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A256GCM),
            Payload(payload)
        )
        jwe.encrypt(DirectEncrypter(encryptionKey))
        return jwe.serialize()
    }

    override fun dispose() {}

    companion object {
        private val LOG = getLogger<CfnCredentialsService>()

        fun getInstance(project: Project): CfnCredentialsService = project.service()

        private fun generateKey(): SecretKey {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return SecretKeySpec(bytes, "AES")
        }
    }
}

internal data class IamCredentials(
    val profile: String,
    val region: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null,
)
