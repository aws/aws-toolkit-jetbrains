// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.components.ServiceManager
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider

class MockCredentialsManager : CredentialManager() {
    fun reset() {
        toolkitCredentialFactories.clear()
        awsCredentialProviderCache.clear()

        providerAdded(DUMMY_PROVIDER)
    }

    fun addCredentials(
        id: String,
        credentials: AwsCredentials = AwsBasicCredentials.create("Access", "Secret")
    ): ToolkitCredentialsProvider = ToolkitCredentialsProvider(
        MockCredentialIdentifier(id),
        StaticCredentialsProvider.create(credentials)
    ).also {
        providerAdded(it)
    }

    companion object {
        fun getInstance(): MockCredentialsManager = ServiceManager.getService(CredentialManager::class.java) as MockCredentialsManager

        val DUMMY_PROVIDER_IDENTIFIER: ToolkitCredentialsIdentifier = MockCredentialIdentifier("DUMMY_CREDENTIALS")
        val DUMMY_PROVIDER_ID = DUMMY_PROVIDER_IDENTIFIER.id

        private val DUMMY_PROVIDER = ToolkitCredentialsProvider(
            DUMMY_PROVIDER_IDENTIFIER,
            StaticCredentialsProvider.create(AwsBasicCredentials.create("DummyAccess", "DummySecret"))
        )
    }

    private class MockCredentialIdentifier(override val displayName: String) : ToolkitCredentialsIdentifier() {
        override val id: String = "mock:$displayName"
    }
}
