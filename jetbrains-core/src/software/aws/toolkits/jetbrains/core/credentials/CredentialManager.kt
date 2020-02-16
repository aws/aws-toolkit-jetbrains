// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import java.util.concurrent.ConcurrentHashMap
import javax.security.auth.login.CredentialNotFoundException

data class CredentialIdentifierChange(
    val added: List<ToolkitCredentialsIdentifier>,
    val modified: List<ToolkitCredentialsIdentifier>,
    val removed: List<ToolkitCredentialsIdentifier>
)

typealias CredentialsChangeListener = (change: CredentialIdentifierChange) -> Unit

abstract class CredentialManager : SimpleModificationTracker() {
    private val providerIds = ConcurrentHashMap<String, ToolkitCredentialsIdentifier>()
    private val awsCredentialProviderCache = ConcurrentHashMap<ToolkitCredentialsIdentifier, ConcurrentHashMap<String, ToolkitCredentialsProvider>>()

    protected abstract fun factoryMapping(): Map<String, CredentialProviderFactory>

    @Throws(CredentialProviderNotFound::class)
    fun getAwsCredentialProvider(providerId: ToolkitCredentialsIdentifier, region: AwsRegion): ToolkitCredentialsProvider {
        val partitionCache = awsCredentialProviderCache.computeIfAbsent(providerId) { _ -> ConcurrentHashMap() }

        // If we already resolved creds for this partition and provider ID, just return it
        partitionCache[region.partitionId]?.let { return it }

        val providerFactory = factoryMapping()[providerId.factoryId]
            ?: throw CredentialNotFoundException("No provider found with ID ${providerId.id}")

        val sdkClient = AwsSdkClient.getInstance()
        val awsCredentialProvider = providerFactory.createAwsCredentialProvider(providerId, region, sdkClient.sdkHttpClient)

        partitionCache[region.partitionId] = awsCredentialProvider

        return awsCredentialProvider
    }

    fun getCredentialIdentifiers(): List<ToolkitCredentialsIdentifier> = providerIds.values.toList()

    fun getCredentialIdentifierById(id: String): ToolkitCredentialsIdentifier? = providerIds[id]

    // TODO: Convert these to bulk listeners so we only send N messages where N is # of extensions vs # of providers
    protected fun addProvider(identifier: ToolkitCredentialsIdentifier) {
        providerIds[identifier.id] = identifier

        incModificationCount()
        ApplicationManager.getApplication().messageBus.syncPublisher(CREDENTIALS_CHANGED).providerAdded(identifier)
    }

    protected fun modifyProvider(identifier: ToolkitCredentialsIdentifier) {
        incModificationCount()
        ApplicationManager.getApplication().messageBus.syncPublisher(CREDENTIALS_CHANGED).providerModified(identifier)
    }

    protected fun removeProvider(identifier: ToolkitCredentialsIdentifier) {
        providerIds.remove(identifier.id)
        awsCredentialProviderCache.remove(identifier)

        incModificationCount()
        ApplicationManager.getApplication().messageBus.syncPublisher(CREDENTIALS_CHANGED).providerRemoved(identifier)
    }

    companion object {
        @JvmStatic
        fun getInstance(): CredentialManager = ServiceManager.getService(CredentialManager::class.java)

        /***
         * [MessageBus] topic for when credential providers get added/changed/deleted
         */
        val CREDENTIALS_CHANGED: Topic<ToolkitCredentialsChangeListener> = Topic.create(
            "AWS toolkit credential providers changed",
            ToolkitCredentialsChangeListener::class.java
        )
    }
}

class DefaultCredentialManager : CredentialManager() {
    private val rootDisposable = Disposer.newDisposable()

    private val extensionMap: Map<String, CredentialProviderFactory> by lazy {
        EP_NAME.extensionList.associate {
            val providerFactory = it.getInstance()
            if (providerFactory is Disposable) {
                Disposer.register(rootDisposable, providerFactory)
            }

            providerFactory.id to providerFactory
        }
    }

    init {
        Disposer.register(ApplicationManager.getApplication(), rootDisposable)

        extensionMap.values.forEach { providerFactory ->
            LOG.tryOrNull("Failed to set up $providerFactory") {
                providerFactory.setUp { change ->
                    change.added.forEach {
                        addProvider(it)
                    }

                    change.modified.forEach {
                        modifyProvider(it)
                    }

                    change.removed.forEach {
                        removeProvider(it)
                    }
                }
            }
        }
    }

    override fun factoryMapping(): Map<String, CredentialProviderFactory> = extensionMap

    private companion object {
        val EP_NAME = ExtensionPointName.create<CredentialProviderFactoryExtensionPoint>("aws.toolkit.credentialProviderFactory")
        val LOG = getLogger<DefaultCredentialManager>()
    }
}

fun AwsCredentials.toEnvironmentVariables(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    map["AWS_ACCESS_KEY"] = this.accessKeyId()
    map["AWS_ACCESS_KEY_ID"] = this.accessKeyId()
    map["AWS_SECRET_KEY"] = this.secretAccessKey()
    map["AWS_SECRET_ACCESS_KEY"] = this.secretAccessKey()

    if (this is AwsSessionCredentials) {
        map["AWS_SESSION_TOKEN"] = this.sessionToken()
        map["AWS_SECURITY_TOKEN"] = this.sessionToken()
    }

    return map
}
