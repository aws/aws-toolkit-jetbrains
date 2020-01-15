// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.util.LazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.AwsSdkClient

/**
 * Extension point for adding new credential providers to the internal registry
 */
interface CredentialProviderFactory {
    fun setupToolkitCredentialProviderFactory(manager: CredentialManager)

    fun createAwsCredentialProvider(
        providerId: ToolkitCredentialsIdentifier,
        region: AwsRegion,
        sdkClient: AwsSdkClient
    ): AwsCredentialsProvider
}

class CredentialProviderFactoryExtensionPoint : AbstractExtensionPointBean() {
    @Attribute("implementation")
    lateinit var implementation: String

    private val instance = object : LazyInstance<CredentialProviderFactory>() {
        override fun getInstanceClass(): Class<CredentialProviderFactory> = findClass(implementation)
    }

    fun getInstance(): CredentialProviderFactory = instance.value
}
