// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider

abstract class CredentialManager : SimpleModificationTracker() {
    @Throws(CredentialProviderNotFound::class)
    abstract fun getCredentialProvider(providerId: String): ToolkitCredentialsProvider

    abstract fun getCredentialProviders(): List<ToolkitCredentialsProvider>

    companion object {
        fun getInstance(): CredentialManager = ServiceManager.getService(CredentialManager::class.java)

        /***
         * [MessageBus] topic for when credential providers get added/changed/deleted
         */
        val CREDENTIALS_CHANGED: Topic<ToolkitCredentialsChangeListener> =
            Topic.create(
                "AWS toolkit credential providers changed",
                ToolkitCredentialsChangeListener::class.java
            )
    }
}

fun AwsCredentials.toEnvironmentVariables(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    map["AWS_ACCESS_KEY"] = this.accessKeyId()
    map["AWS_ACCESS_KEY_ID"] = this.accessKeyId()
    map["AWS_SECRET_KEY"] = this.secretAccessKey()
    map["AWS_SECRET_ACCESS_KEY"] = this.secretAccessKey()

    if (this is AwsSessionCredentials) {
        map["AWS_SESSION_TOKEN"] = this.sessionToken()
        map["AWS_SECURITY_TOKEN"] = this.sessionToken()
    }

    return map
}