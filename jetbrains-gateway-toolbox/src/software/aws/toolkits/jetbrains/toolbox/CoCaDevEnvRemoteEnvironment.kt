// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.AbstractRemoteProviderEnvironment
import com.jetbrains.toolbox.gateway.EnvironmentVisibilityState
import com.jetbrains.toolbox.gateway.RemoteProviderEnvironment
import com.jetbrains.toolbox.gateway.environments.EnvironmentContentsView
import com.jetbrains.toolbox.gateway.states.EnvironmentStateConsumer
import kotlinx.coroutines.CoroutineScope
import software.aws.toolkits.jetbrains.services.caws.CawsProject
import java.util.concurrent.CompletableFuture

class CoCaDevEnvRemoteEnvironment(
    private val coroutineScope: CoroutineScope,
    private val project: CawsProject
): AbstractRemoteProviderEnvironment() {
    override fun getId(): String {
        return project.toString()
    }

    override fun getName(): String {
        return project.toString()
    }

    override fun getContentsView(): CompletableFuture<EnvironmentContentsView> {
        return CompletableFuture.completedFuture(CoCaDevEnvRemoteEnvironmentContentsView(coroutineScope))
    }

    override fun setVisible(visibilityState: EnvironmentVisibilityState) {
    }
}
