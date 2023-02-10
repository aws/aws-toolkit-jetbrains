// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr.actions

import com.intellij.docker.runtimes.DockerServerRuntime
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.services.ecr.EcrRepositoryNode
import software.aws.toolkits.jetbrains.services.ecr.EcrUtils

abstract class EcrDockerAction :
    SingleExplorerNodeAction<EcrRepositoryNode>(),
    DumbAware {

    protected companion object {
        fun CoroutineScope.dockerServerRuntimeAsync(project: Project): Deferred<DockerServerRuntime> =
            async(start = CoroutineStart.LAZY) { EcrUtils.getDockerServerRuntimeInstance(project) }
    }
}
