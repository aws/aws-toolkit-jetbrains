// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr.actions

import com.intellij.docker.DockerCloudConfiguration
import com.intellij.docker.DockerCloudType
import com.intellij.docker.DockerDeploymentConfiguration
import com.intellij.docker.DockerServerRuntimeInstance
import com.intellij.docker.registry.DockerRegistry
import com.intellij.docker.registry.DockerRepositoryModel
import com.intellij.docker.runtimes.DockerApplicationRuntime
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.remoteServer.impl.configuration.RemoteServerImpl
import com.intellij.remoteServer.runtime.ServerConnectionManager
import com.intellij.remoteServer.runtime.ServerConnector
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.listCellRenderer
import com.intellij.ui.layout.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.await
import software.amazon.awssdk.services.ecr.EcrClient
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.services.ecr.EcrPushRequest
import software.aws.toolkits.jetbrains.services.ecr.getDockerLogin
import software.aws.toolkits.jetbrains.services.ecr.resources.EcrResources
import software.aws.toolkits.jetbrains.services.ecr.toLocalImageList
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.ui.blankAsNull
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message

class PushTagToRepositoryAction :
    DumbAwareAction(message("ecr.push.action")),
    CoroutineScope by ApplicationThreadPoolScope("PushRepositoryAction") {
    private val dockerServerRuntime: Deferred<DockerServerRuntimeInstance> =
        async(start = CoroutineStart.LAZY) { getDockerServerRuntimeInstance() }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: EcrClient = project.awsClient()

        val dialog = PushToEcrDialog(project, dockerServerRuntime)
        val result = dialog.showAndGet()

        if (!result) {
            // user cancelled; noop
            return
        }
        val pushRequest = dialog.getPushRequest()

        launch {
            val authData = withContext(Dispatchers.IO) {
                client.authorizationToken.authorizationData().first()
            }

            val (username, password) = authData.getDockerLogin()
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

            val dockerApplicationRuntime = getDockerApplicationRuntimeInstance(dockerServerRuntime.await(), pushRequest.localImageId)
            dockerApplicationRuntime.pushImage(project, model)
        }
    }

    private suspend fun getDockerServerRuntimeInstance(): DockerServerRuntimeInstance {
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

    private suspend fun getDockerApplicationRuntimeInstance(serverRuntime: DockerServerRuntimeInstance, imageId: String): DockerApplicationRuntime =
        serverRuntime.findRuntimeLater(imageId, false).await()
}

internal data class LocalImage(
    val imageId: String,
    val tag: String?
)

internal class PushToEcrDialog(
    project: Project,
    private val dockerServerRuntime: Deferred<DockerServerRuntimeInstance>
) : DialogWrapper(project, null, false, IdeModalityType.PROJECT),
    CoroutineScope by ApplicationThreadPoolScope("PushRepositoryDialog") {
    private var localImage: LocalImage? = null
    private var remoteTag: String = "latest"
    private val localImageRepoTags = CollectionComboBoxModel<LocalImage>()

    private val remoteRepos = ResourceSelector.builder()
        .resource(EcrResources.LIST_REPOS)
        .customRenderer(SimpleListCellRenderer.create("") { it.repositoryName })
        .awsConnection(project)
        .build()

    init {
        title = message("ecr.push.title")
        setOKButtonText(message("ecr.push.confirm"))

        init()

        launch {
            val serverRuntime = dockerServerRuntime.await()
            val images = serverRuntime.agent.getImages(null)
            localImageRepoTags.add(images.toLocalImageList())
            localImageRepoTags.update()
        }
    }

    override fun createCenterPanel() = panel {
        row(message("ecr.repo.label")) {
            component(remoteRepos)
        }

        row(message("ecr.push.source")) {
            // property binding syntax causes kotlin compiler error for some reason
            comboBox(
                localImageRepoTags,
                { localImage },
                { ::localImage.set(it) },
                listCellRenderer { value, _, _ ->
                    value.tag ?: value.imageId.take(15)
                }
            ).withErrorOnApplyIf(message("ecr.repo.not_selected")) { it.selected() == null }
        }

        row(message("ecr.push.remoteTag")) {
            textField(::remoteTag)
                .withErrorOnApplyIf("local image not specified") { it.blankAsNull() == null }
        }
    }

    fun getPushRequest() = EcrPushRequest(
        localImage?.imageId ?: throw IllegalStateException("image id was null"),
        remoteRepos.selected() ?: throw IllegalStateException("repository uri was null"),
        remoteTag
    )
}
