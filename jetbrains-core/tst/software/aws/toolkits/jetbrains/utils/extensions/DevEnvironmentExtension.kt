// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.extensions

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import software.amazon.awssdk.services.codecatalyst.CodeCatalystClient
import software.amazon.awssdk.services.codecatalyst.model.CreateDevEnvironmentRequest
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.DiskSsoSessionConnection

class DevEnvironmentExtension(environmentBuilder: (CreateDevEnvironmentRequest.Builder) -> Unit) : AfterAllCallback {
    private val client: CodeCatalystClient by lazy {
        val provider = DiskSsoSessionConnection("codecatalyst", "us-east-1")
        AwsClientManager.getInstance().getClient(provider.getConnectionSettings())
    }

    private val lazyEnvironment = lazy {
        client.createDevEnvironment {
            environmentBuilder(it)
        }.let {
            DevEnvironment(
                spaceName = it.spaceName(),
                projectName = it.projectName(),
                id = it.id()
            )
        }
    }
    val environment: DevEnvironment by lazyEnvironment

    override fun afterAll(context: ExtensionContext) {
        if (lazyEnvironment.isInitialized()) {
            client.deleteDevEnvironment {
                it.spaceName(environment.spaceName)
                it.projectName(environment.projectName)
                it.id(environment.id)
            }
        }
    }
}

data class DevEnvironment(
    val spaceName: String,
    val projectName: String,
    val id: String
)
