// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr

import com.intellij.docker.DockerCloudConfiguration
import com.intellij.docker.DockerCloudType
import com.intellij.docker.DockerDeploymentConfiguration
import com.intellij.docker.DockerServerRuntimeInstance
import com.intellij.docker.agent.DockerAgentApplication
import com.intellij.docker.registry.DockerRegistry
import com.intellij.docker.registry.DockerRepositoryModel
import com.intellij.docker.runtimes.DockerApplicationRuntime
import com.intellij.openapi.project.Project
import com.intellij.remoteServer.impl.configuration.RemoteServerImpl
import com.intellij.remoteServer.runtime.ServerConnectionManager
import com.intellij.remoteServer.runtime.ServerConnector
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance
import com.intellij.util.Base64
import kotlinx.coroutines.future.await
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.await
import software.amazon.awssdk.services.ecr.model.AuthorizationData
import software.aws.toolkits.jetbrains.services.ecr.actions.LocalImage
import software.aws.toolkits.jetbrains.services.ecr.resources.Repository

data class EcrLogin(
    val username: String,
    val password: String
)

data class EcrPushRequest(
    val localImageId: String,
    val remoteRepo: Repository,
    val remoteTag: String
)

suspend fun getDockerServerRuntimeInstance(): DockerServerRuntimeInstance {
    val instancePromise = AsyncPromise<DockerServerRuntimeInstance>()
    val connection = ServerConnectionManager.getInstance().createTemporaryConnection(
        RemoteServerImpl("DockerConnection", DockerCloudType.getInstance(), DockerCloudConfiguration.createDefault())
    )
    connection.connectIfNeeded(object : ServerConnector.ConnectionCallback<DockerDeploymentConfiguration> {
        override fun errorOccurred(errorMessage: String) {
            instancePromise.setError(errorMessage)
        }

        override fun connected(serverRuntimeInstance: ServerRuntimeInstance<DockerDeploymentConfiguration>) {
            instancePromise.setResult(serverRuntimeInstance as DockerServerRuntimeInstance)
        }
    })

    return instancePromise.await()
}

suspend fun getDockerApplicationRuntimeInstance(serverRuntime: DockerServerRuntimeInstance, imageId: String): DockerApplicationRuntime =
    serverRuntime.findRuntimeLater(imageId, false).await()

suspend fun pushImage(project: Project, dockerServerRuntime: DockerServerRuntimeInstance, ecrLogin: EcrLogin, pushRequest: EcrPushRequest) {
    val (username, password) = ecrLogin
    val model = DockerRepositoryModel().also {
        val repoUri = pushRequest.remoteRepo.repositoryUri
        // fix
        it.registry = DockerRegistry().also { registry ->
            registry.address = repoUri
            registry.username = username
            registry.password = password
        }
        it.repository = repoUri
        it.tag = pushRequest.remoteTag
    }

    val dockerApplicationRuntime = getDockerApplicationRuntimeInstance(dockerServerRuntime, pushRequest.localImageId)
    dockerApplicationRuntime.pushImage(project, model)
}

private const val NO_TAG_TAG = "<none>:<none>"
internal fun Array<DockerAgentApplication>.toLocalImageList(): List<LocalImage> =
    this.flatMap { image ->
        image.imageRepoTags?.map { localTag ->
            val tag = if (localTag == NO_TAG_TAG) null else localTag
            LocalImage(image.imageId, tag)
        } ?: listOf(LocalImage(image.imageId, null))
    }.toList()

fun AuthorizationData.getDockerLogin(): EcrLogin {
    val auth = Base64.decode(this.authorizationToken()).toString(Charsets.UTF_8).split(':', limit = 2)

    return EcrLogin(
        auth.first(),
        auth.last()
    )
}

fun software.amazon.awssdk.services.ecr.model.Repository.toToolkitEcrRepository(): Repository? {
    val name = repositoryName() ?: return null
    val arn = repositoryArn() ?: return null
    val uri = repositoryUri() ?: return null

    return Repository(name, arn, uri)
}
