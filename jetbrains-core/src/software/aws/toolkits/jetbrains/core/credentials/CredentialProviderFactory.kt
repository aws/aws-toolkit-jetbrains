// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.SdkHttpClient
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.core.region.AwsRegion

/**
 * Extension point for adding new credential providers to the internal registry
 */
interface CredentialProviderFactory {
    /** ID used to uniquely identify this factory */
    val id: String

    fun setUp(credentialLoadCallback: CredentialsChangeListener)

    fun createAwsCredentialProvider(providerId: ToolkitCredentialsIdentifier, region: AwsRegion, sdkClient: SdkHttpClient): AwsCredentialsProvider
}
