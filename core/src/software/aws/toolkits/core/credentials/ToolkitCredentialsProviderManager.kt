// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

import org.slf4j.LoggerFactory
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.core.utils.warn
import java.util.concurrent.ConcurrentHashMap

class ToolkitCredentialsProviderManager(registry: ToolkitCredentialsProviderRegistry) {
    private val listeners = ConcurrentHashMap.newKeySet<ToolkitCredentialsChangeListener>()
    private val factories = mutableListOf<ToolkitCredentialsProviderFactory<*>>()

    init {
        reloadFactories(registry)
    }

    @Throws(CredentialProviderNotFound::class)
    fun getCredentialProvider(id: String): ToolkitCredentialsProvider = factories.asSequence().mapNotNull { it.get(id) }.firstOrNull()
        ?: throw CredentialProviderNotFound("No ToolkitCredentialsProvider found represented by $id")

    /**
     * Returns a list of all the registered providers. This list should never change for the life of the toolkit
     */
    fun getCredentialProviders(): List<ToolkitCredentialsProvider> = factories.flatMap { it.listCredentialProviders() }.toList()

    fun addChangeListener(listener: ToolkitCredentialsChangeListener) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: ToolkitCredentialsChangeListener) {
        listeners.remove(listener)
    }

    fun providerAdded(provider: ToolkitCredentialsProvider) {
        listeners.forEach {
            LOG.tryOrNull("Failed to notify listener that provider was added") {
                it.providerAdded(provider)
            }
        }
    }

    fun providerModified(provider: ToolkitCredentialsProvider) {
        listeners.forEach {
            LOG.tryOrNull("Failed to notify listener that provider was modified") {
                it.providerModified(provider)
            }
        }
    }

    fun providerRemoved(providerId: String) {
        listeners.forEach {
            LOG.tryOrNull("Failed to notify listener that provider was removed") {
                it.providerRemoved(providerId)
            }
        }
    }

    fun reloadFactories(registry: ToolkitCredentialsProviderRegistry) {
        factories.clear()
        factories.addAll(registry.listFactories(this))
    }

    /**
     * Shuts down the manager and all registered factories
     */
    fun shutDown() {
        factories.forEach {
            try {
                it.shutDown()
            } catch (e: Exception) {
                LOG.warn(e) { "ToolkitCredentialsProviderFactory '${it::class.qualifiedName}' threw exception when shutting down" }
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolkitCredentialsProviderManager::class.java)
    }
}

