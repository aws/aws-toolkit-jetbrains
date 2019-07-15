// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

/**
 * The class for managing [ToolkitCredentialsProvider] of the same type.
 * @property type The internal ID for this type of [ToolkitCredentialsProvider], eg 'profile' for AWS account whose credentials is stored in the profile file.
 */
abstract class ToolkitCredentialsProviderFactory<T : ToolkitCredentialsProvider>(
    val type: String,
    protected val credentialsProviderManager: ToolkitCredentialsProviderManager
) {
    private val providers = mutableMapOf<String, T>()

    protected fun add(provider: T) {
        providers[provider.id] = provider
        credentialsProviderManager.providerAdded(provider)
    }

    protected fun remove(provider: T) {
        providers.remove(provider.id)
        credentialsProviderManager.providerRemoved(provider.id)
    }

    fun listCredentialProviders() = providers.values

    fun get(id: String) = providers[id]

    /**
     * Called when the [ToolkitCredentialsProviderManager] is shutting down to allow for resource clean up
     */
    open fun shutDown() {}
}