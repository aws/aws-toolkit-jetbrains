// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.CredentialProviderFactory

class ProfileCredentialProviderFactory : CredentialProviderFactory {
    val profileWatcher = ProfileWatcher().also {
        // TODO: Scope this better in the cred manager refactorDisposer.register(ApplicationManager.getApplication(), it)
    }

    override fun setupToolkitCredentialProviderFactory(manager: CredentialManager) {
        profileWatcher.start()
    }

    override fun createAwsCredentialProvider(region: AwsRegion): AwsCredentialsProvider {
        TODO("not implemented")
    }
}
