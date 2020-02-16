// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.util.LazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import software.amazon.awssdk.http.SdkHttpClient
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion

/**
 * Extension point for adding new credential providers to the internal registry
 */
interface CredentialProviderFactory {
    fun setUp(credentialLoadCallback: CredentialsChangeListener)

    fun createAwsCredentialProvider(providerId: ToolkitCredentialsIdentifier, region: AwsRegion, sdkClient: SdkHttpClient): ToolkitCredentialsProvider
}

class CredentialProviderFactoryExtensionPoint : AbstractExtensionPointBean() {
    @Attribute("implementation")
    lateinit var implementation: String

    private val instance = object : LazyInstance<CredentialProviderFactory>() {
        override fun getInstanceClass(): Class<CredentialProviderFactory> = findClass(implementation)
    }

    fun getInstance(): CredentialProviderFactory = instance.value
}
