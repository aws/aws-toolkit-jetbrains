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

typealias CredentialsChangeListener = (added: List<ToolkitCredentialsIdentifier>, modified: List<ToolkitCredentialsIdentifier>, removed: List<ToolkitCredentialsIdentifier>) -> Unit

abstract class CredentialManager : SimpleModificationTracker(), ToolkitCredentialsChangeListener {
    protected val toolkitCredentialFactories = ConcurrentHashMap<ToolkitCredentialsIdentifier, CredentialProviderFactory>()
    protected val awsCredentialProviderCache = ConcurrentHashMap<ToolkitCredentialsIdentifier, ConcurrentHashMap<String, ToolkitCredentialsProvider>>()

    @Throws(CredentialProviderNotFound::class)
    fun getAwsCredentialProvider(providerId: ToolkitCredentialsIdentifier, region: AwsRegion): ToolkitCredentialsProvider {
        val partitionCache = awsCredentialProviderCache.computeIfAbsent(providerId) { _ -> ConcurrentHashMap() }

        partitionCache[region.partitionId]?.let { return it }

        val providerFactory = toolkitCredentialFactories[providerId]
            ?: throw CredentialNotFoundException("No provider found with ID ${providerId.id}")

        val sdkClient = AwsSdkClient.getInstance()
        val awsCredentialProvider = providerFactory.createAwsCredentialProvider(providerId, region, sdkClient)

        partitionCache[region.partitionId] = awsCredentialProvider

        return awsCredentialProvider
    }

    fun getCredentialIdentifiers() = toolkitCredentialFactories.keys.toList()

    fun getCredentialIdentifier(id: String) = toolkitCredentialFactories.keys.find { it.id == id }

    // TODO: Convert these to bulk listeners so we only send N messages where N is # of extensions vs # of providers
    override fun providerAdded(provider: ToolkitCredentialsProvider) {
        incModificationCount()
        ApplicationManager.getApplication().messageBus.syncPublisher(CREDENTIALS_CHANGED).providerAdded(provider)
    }

    override fun providerModified(provider: ToolkitCredentialsProvider) {
        incModificationCount()
        ApplicationManager.getApplication().messageBus.syncPublisher(CREDENTIALS_CHANGED).providerModified(provider)
    }

    override fun providerRemoved(providerId: String) {
        incModificationCount()
        ApplicationManager.getApplication().messageBus.syncPublisher(CREDENTIALS_CHANGED).providerRemoved(providerId)
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

    init {
        Disposer.register(ApplicationManager.getApplication(), rootDisposable)

        EP_NAME.extensionList.forEach { providerFactory ->
            val instance = providerFactory.getInstance()
            if (instance is Disposable) {
                Disposer.register(rootDisposable, instance)
            }

            LOG.tryOrNull("Failed to setup $instance") {
                instance.setUp { added, modified, removed ->
                    added.forEach {
                        toolkitCredentialFactories[it] = instance
                    }

                    modified.forEach {
                        toolkitCredentialFactories[it] = instance
                        awsCredentialProviderCache.remove(it)
                    }

                    removed.forEach {
                        toolkitCredentialFactories.remove(it)
                        awsCredentialProviderCache.remove(it)
                    }

                    incModificationCount()
                }
            }
        }
    }

    companion object {
        private val EP_NAME = ExtensionPointName.create<CredentialProviderFactoryExtensionPoint>("aws.toolkit.credentialProviderFactory")
        private val LOG = getLogger<DefaultCredentialManager>()
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
