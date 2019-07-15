// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.credentials.ToolkitCredentialsProviderManager

class DefaultCredentialManager : CredentialManager(), Disposable {
    private val toolkitCredentialManager = ToolkitCredentialsProviderManager(
            ExtensionPointCredentialsProviderRegistry()
        )

    // Re-map the listener on the core ToolkitProviderManager to a message bus for the IDE
    private val listener = object : ToolkitCredentialsChangeListener {
        override fun providerAdded(provider: ToolkitCredentialsProvider) {
            incModificationCount()
            ApplicationManager.getApplication()
                .messageBus.syncPublisher(CREDENTIALS_CHANGED).providerAdded(provider)
        }

        override fun providerModified(provider: ToolkitCredentialsProvider) {
            incModificationCount()
            ApplicationManager.getApplication()
                .messageBus.syncPublisher(CREDENTIALS_CHANGED).providerModified(provider)
        }

        override fun providerRemoved(providerId: String) {
            incModificationCount()
            ApplicationManager.getApplication()
                .messageBus.syncPublisher(CREDENTIALS_CHANGED).providerRemoved(providerId)
        }
    }

    init {
        Disposer.register(
            ApplicationManager.getApplication(),
            this
        )
        toolkitCredentialManager.addChangeListener(listener)
    }

    @Throws(CredentialProviderNotFound::class)
    override fun getCredentialProvider(providerId: String): ToolkitCredentialsProvider =
        toolkitCredentialManager.getCredentialProvider(providerId)

    override fun getCredentialProviders(): List<ToolkitCredentialsProvider> =
        toolkitCredentialManager.getCredentialProviders()

    override fun dispose() {
        toolkitCredentialManager.removeChangeListener(listener)
        toolkitCredentialManager.shutDown()
    }
}