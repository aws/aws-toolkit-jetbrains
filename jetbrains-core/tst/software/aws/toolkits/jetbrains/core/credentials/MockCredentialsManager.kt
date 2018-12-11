// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.components.ServiceManager
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.http.SdkHttpClient
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider

class MockCredentialsManager : CredentialManager {
    private val providers = mutableMapOf<String, ToolkitCredentialsProvider>()

    override fun getCredentialProviders(): List<ToolkitCredentialsProvider> = providers.values.toList()

    override fun getCredentialProvider(providerId: String): ToolkitCredentialsProvider = providers[providerId]
        ?: throw CredentialProviderNotFound("$providerId not found")

    fun reset() {
        providers.clear()
    }

    fun addCredentials(id: String, credentials: AwsCredentials, isValid: Boolean = true): ToolkitCredentialsProvider = MockCredentialsProvider(id, id, credentials, isValid).also {
        providers[id] = it
    }

    companion object {
        fun getInstance(): MockCredentialsManager = ServiceManager.getService(CredentialManager::class.java) as MockCredentialsManager
    }

    private inner class MockCredentialsProvider(
        override val id: String,
        override val displayName: String,
        private val credentials: AwsCredentials,
        private val isValid: Boolean
    ) : ToolkitCredentialsProvider() {
        override fun resolveCredentials(): AwsCredentials = credentials
        override fun isValid(sdkHttpClient: SdkHttpClient): Boolean = isValid
    }
}