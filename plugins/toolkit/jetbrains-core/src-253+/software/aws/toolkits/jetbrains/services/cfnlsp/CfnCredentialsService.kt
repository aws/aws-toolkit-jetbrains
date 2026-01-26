// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.DirectEncrypter
import org.eclipse.lsp4j.DidChangeConfigurationParams
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkit.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkit.jetbrains.core.credentials.ConnectionState
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider
import software.aws.toolkits.jetbrains.settings.CfnLspSettingsChangeListener
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
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

    init {
        val appBus = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect(this)
        subscribeToCredentialChanges(appBus)
        subscribeToSettingsChanges(appBus)
    }

    val encryptionKeyBase64: String
        get() = Base64.getEncoder().encodeToString(encryptionKey.encoded)

    fun sendCredentialsToServer() {
        val server = findLspServer() ?: return

        val credentials = resolveCredentials()
        if (credentials != null) {
            val encrypted = encrypt(credentials)
            server.sendNotification { lsp ->
                (lsp as? CfnLspServer)?.updateIamCredentials(
                    UpdateCredentialsParams(encrypted, true)
                )?.whenComplete { result, error ->
                    if (error != null) {
                        LOG.warn(error) { "Failed to update credentials on LSP server" }
                    } else {
                        LOG.info { "Credentials updated on LSP server: success=${result?.success}" }
                    }
                }
            }
        } else {
            server.sendNotification { lsp ->
                (lsp as? CfnLspServer)?.deleteIamCredentials()
            }
            LOG.debug { "Credentials deleted from LSP server" }
        }
    }

    fun notifyConfigurationChanged() {
        val server = findLspServer() ?: return
        server.sendNotification { lsp ->
            lsp.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>()))
        }
        LOG.info { "Sent didChangeConfiguration to LSP server" }
    }

    private fun subscribeToCredentialChanges(appBus: com.intellij.util.messages.MessageBusConnection) {
        appBus.subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    sendCredentialsToServer()
                }
            }
        )

        project.messageBus.connect(this).subscribe(
            AwsConnectionManager.CONNECTION_SETTINGS_STATE_CHANGED,
            object : ConnectionSettingsStateChangeNotifier {
                override fun settingsStateChanged(newState: ConnectionState) {
                    sendCredentialsToServer()
                }
            }
        )
    }

    private fun subscribeToSettingsChanges(appBus: com.intellij.util.messages.MessageBusConnection) {
        appBus.subscribe(CfnLspSettingsChangeListener.TOPIC, CfnLspSettingsChangeListener {
            notifyConfigurationChanged()
        })
    }

    private fun resolveCredentials(): IamCredentials? {
        val connectionManager = AwsConnectionManager.getInstance(project)
        val credentialProvider = connectionManager.activeCredentialProvider ?: return null
        val region = connectionManager.activeRegion ?: return null

        return try {
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
        val payload = """{"data":${toJson(credentials)}}"""
        val jwe = JWEObject(
            JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A256GCM),
            Payload(payload)
        )
        jwe.encrypt(DirectEncrypter(encryptionKey))
        return jwe.serialize()
    }

    private fun toJson(credentials: IamCredentials): String = buildString {
        append("{")
        append(""""profile":"${credentials.profile}",""")
        append(""""region":"${credentials.region}",""")
        append(""""accessKeyId":"${credentials.accessKeyId}",""")
        append(""""secretAccessKey":"${credentials.secretAccessKey}"""")
        credentials.sessionToken?.let { append(""","sessionToken":"$it"""") }
        append("}")
    }

    @Suppress("UnstableApiUsage")
    private fun findLspServer(): LspServer? =
        LspServerManager.getInstance(project)
            .getServersForProvider(CfnLspServerSupportProvider::class.java)
            .firstOrNull()

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

private data class IamCredentials(
    val profile: String,
    val region: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null
)
