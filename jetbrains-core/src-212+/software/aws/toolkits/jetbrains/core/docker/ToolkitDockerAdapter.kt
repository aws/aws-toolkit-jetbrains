// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.docker

import com.intellij.docker.DockerAgentPathMapperImpl
import com.intellij.docker.DockerDeploymentConfiguration
import com.intellij.docker.DockerServerRuntimeInstance
import com.intellij.docker.agent.DockerAgentLogProvider
import com.intellij.docker.agent.DockerAgentSourceType
import com.intellij.docker.agent.progress.DockerResponseItem
import com.intellij.docker.agent.terminal.pipe.DockerTerminalPipe
import com.intellij.docker.registry.DockerRepositoryModel
import com.intellij.docker.remote.run.runtime.DockerAgentBuildImageConfig
import com.intellij.docker.remote.run.runtime.DockerAgentDeploymentConfigImpl
import com.intellij.docker.runtimes.DockerImageRuntime
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.await
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.ecr.DockerfileEcrPushRequest
import java.io.File
import java.io.ObjectInputStream
import java.time.Instant

class ToolkitDockerAdapter(project: Project, serverRuntime: DockerServerRuntimeInstance) : AbstractToolkitDockerAdapter(project, serverRuntime) {
    override fun buildLocalImage(dockerfile: File): String? {
        val deployment = serverRuntime.agent.createDeployment(
            DockerAgentBuildImageConfig(System.currentTimeMillis().toString(), dockerfile, false),
            DockerAgentPathMapperImpl(project)
        )

        val logger = DockerAgentLogProvider(
            infoFunction = LOG::info,
            traceFunction = LOG::trace,
            warnFunction = LOG::warn,
            errorFunction = LOG::error,
            isTraceEnabled = false
        )

        return deployment.deploy("untagged test image", DockerTerminalPipe("AWS Toolkit build for $dockerfile", logger), null)?.imageId
    }

    override suspend fun hackyBuildDockerfileWithUi(project: Project, pushRequest: DockerfileEcrPushRequest): String? {
        val dockerConfig = (pushRequest.dockerBuildConfiguration.deploymentConfiguration as DockerDeploymentConfiguration)
        val tag = dockerConfig.separateImageTags.firstOrNull() ?: Instant.now().toEpochMilli().toString()
        val config = object : DockerAgentDeploymentConfigImpl(tag, null) {
            override fun getFile() =
                dockerConfig.sourceFilePath?.let {
                    File(it)
                    // should never be null
                } ?: throw RuntimeException("Docker run configuration started with invalid source file")

            override fun sourceType() = DockerAgentSourceType.FILE.toString()

            override fun getCustomContextFolder() =
                dockerConfig.contextFolderPath?.let {
                    File(it)
                } ?: super.getCustomContextFolder()
        }.withEnvs(dockerConfig.envVars.toTypedArray())
            .withBuildArgs(dockerConfig.buildArgs.toTypedArray())

        val queue = serverRuntime.agent.createImageBuilder().asyncBuildImage(config).await()
        while (true) {
            val obj = queue.take()
            if (obj.isEmpty()) {
                break
            }
            LOG.debug {
                val deserialized = ObjectInputStream(obj.inputStream()).readObject()
                (deserialized as? DockerResponseItem)?.stream ?: deserialized.toString()
            }
        }

        return serverRuntime.agent.getImages(null).firstOrNull { it.imageRepoTags.contains("$tag:latest") }?.imageId
    }

    override suspend fun pushImage(localTag: String, config: DockerRepositoryModel) {
        val physicalLocalRuntime = serverRuntime.findRuntimeLater(localTag, false).await()
        (physicalLocalRuntime as? DockerImageRuntime)?.pushImage(project, config) ?: LOG.error { "couldn't map tag to appropriate docker runtime" }
    }

    companion object {
        val LOG = getLogger<ToolkitDockerAdapter>()
    }
}
