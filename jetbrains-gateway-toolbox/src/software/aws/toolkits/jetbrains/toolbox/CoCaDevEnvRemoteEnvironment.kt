// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.EnvironmentVisibilityState
import com.jetbrains.toolbox.gateway.RemoteEnvironmentPropertiesConsumer
import com.jetbrains.toolbox.gateway.RemoteProviderEnvironment
import com.jetbrains.toolbox.gateway.environments.EnvironmentContentsView
import software.aws.toolkits.jetbrains.services.caws.CawsProject
import java.util.concurrent.CompletableFuture

class CoCaDevEnvRemoteEnvironment(
    private val project: CawsProject
): RemoteProviderEnvironment {
    override fun getId(): String {
        return project.toString()
    }

    override fun getName(): String {
        return project.toString()
    }

    override fun addStateListener(consumer: RemoteEnvironmentPropertiesConsumer?) {
    }

    override fun removeStateListener(consumer: RemoteEnvironmentPropertiesConsumer?) {
    }

    override fun getContentsView(): CompletableFuture<EnvironmentContentsView> {
        return CompletableFuture.completedFuture(CoCaDevEnvRemoteEnvironmentContentsView())
    }

    override fun setVisible(visibilityState: EnvironmentVisibilityState) {
    }
}
