// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.ProviderVisibilityState
import com.jetbrains.toolbox.gateway.RemoteEnvironmentConsumer
import com.jetbrains.toolbox.gateway.RemoteProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sono.SonoCredentialManager
import software.aws.toolkits.jetbrains.core.credentials.tokenConnection
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.caws.CawsResources
import java.net.URI

class CoCaDevEnvRemoteProvider(
    private val consumer: RemoteEnvironmentConsumer,
    coroutineScope: CoroutineScope
) : RemoteProvider {

    init {
        coroutineScope.launch {
            val connectionSettings = tokenConnection(
                InteractiveBearerTokenProviderNoOpenApi(
                    SONO_URL,
                    SONO_REGION,
                    CODECATALYST_SCOPES
                ),
                SONO_REGION
            )

            AwsResourceCache.getInstance().getResource(CawsResources.ALL_PROJECTS_ASYNC, connectionSettings).toCompletableFuture().get().forEach {
                consumer.consumeEnvironments(
                    listOf(CoCaDevEnvRemoteEnvironment(it))
                )
            }
        }
    }

    override fun close() {}

    override fun getName(): String {
        return "sample name"
    }

    override fun canCreateNewEnvironments(): Boolean = true

    override fun isSingleEnvironment(): Boolean = false

    override fun setVisibilityState(visibilityState: ProviderVisibilityState) {
    }

    override fun addEnvironmentsListener(listener: RemoteEnvironmentConsumer?) {
    }

    override fun removeEnvironmentsListener(listener: RemoteEnvironmentConsumer?) {
    }

    override fun handleUri(uri: URI) {
        println("handleUri: $uri")
    }
}
