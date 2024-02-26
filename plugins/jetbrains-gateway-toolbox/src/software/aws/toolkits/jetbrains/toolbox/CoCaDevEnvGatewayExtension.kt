// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.GatewayExtension
import com.jetbrains.toolbox.gateway.RemoteEnvironmentConsumer
import com.jetbrains.toolbox.gateway.RemoteProvider
import com.jetbrains.toolbox.gateway.ToolboxServiceLocator
import kotlinx.coroutines.CoroutineScope

class CoCaDevEnvGatewayExtension : GatewayExtension {
    override fun createRemoteProviderPluginInstance(serviceLocator: ToolboxServiceLocator): RemoteProvider =
        CoCaDevEnvRemoteProvider(
            serviceLocator.getService(RemoteEnvironmentConsumer::class.java),
            serviceLocator.getService(CoroutineScope::class.java)
        )
}
