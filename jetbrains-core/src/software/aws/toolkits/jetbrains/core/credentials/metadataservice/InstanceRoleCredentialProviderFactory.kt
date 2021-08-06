// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.metadataservice

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.core.SdkSystemSetting
import software.amazon.awssdk.http.HttpExecuteRequest
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.SdkHttpRequest
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.core.credentials.CredentialProviderFactory
import software.aws.toolkits.core.credentials.CredentialSourceId
import software.aws.toolkits.core.credentials.CredentialType
import software.aws.toolkits.core.credentials.CredentialsChangeEvent
import software.aws.toolkits.core.credentials.CredentialsChangeListener
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import java.io.IOException
import java.net.URI

class InstanceRoleCredentialProviderFactory : CredentialProviderFactory {
    override val id = FACTORY_ID
    override val credentialSourceId: CredentialSourceId = CredentialSourceId.Ec2

    private val instanceRoleCredIdentifier: CredentialIdentifier by lazy {
        object : CredentialIdentifier {
            override val id: String = "ec2InstanceRoleCredential"
            override val displayName = "ec2:instanceProfile"
            override val factoryId = FACTORY_ID
            override val credentialType: CredentialType = CredentialType.Ec2Metadata
        }
    }

    override fun setUp(credentialLoadCallback: CredentialsChangeListener) {
        if (SdkSystemSetting.AWS_EC2_METADATA_DISABLED.booleanValue.orElse(false)) {
            LOG.debug { "EC2 metadata provider disabled by system setting" }
            return
        }

        val endpoint = SdkSystemSetting.AWS_EC2_METADATA_SERVICE_ENDPOINT.stringValue.orElse("")
        if (endpoint.isBlank()) {
            LOG.debug { "Skipping instance role credential provider since endpoint was blank" }
            return
        }

        val client = AwsSdkClient.getInstance().sharedSdkClient()
        val uri = URI.create(endpoint)
        // URI = scheme:[//authority]path[?query][#fragment]
        val request = client.prepareRequest(
            HttpExecuteRequest.builder().request(
                SdkHttpRequest.builder()
                    .method(SdkHttpMethod.HEAD)
                    .protocol(uri.scheme)
                    .host(uri.authority)
                    .build()
            ).build()
        )

        try {
            val response = request.call().httpResponse()
            if (!response.isSuccessful) {
                LOG.debug { "HEAD request to ec2 metadata failed" }
                return
            }
        } catch (e: IOException) {
            LOG.debug(e) { "Skipping instance role credential provider since endpoint failed to connect" }
            return
        }

        credentialLoadCallback(
            CredentialsChangeEvent(
                added = listOf(instanceRoleCredIdentifier),
                modified = emptyList(),
                removed = emptyList()
            )
        )
    }

    override fun createAwsCredentialProvider(providerId: CredentialIdentifier, region: AwsRegion): AwsCredentialsProvider =
        InstanceProfileCredentialsProvider.builder()
            .asyncCredentialUpdateEnabled(false)
            .build()

    companion object {
        const val FACTORY_ID = "InstanceRoleCredentialProviderFactory"
        private val LOG = getLogger<InstanceRoleCredentialProviderFactory>()
    }
}
