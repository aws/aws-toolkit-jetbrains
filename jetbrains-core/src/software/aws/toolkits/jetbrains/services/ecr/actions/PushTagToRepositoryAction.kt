// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr.actions

import com.intellij.docker.DockerServerRuntimeInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.listCellRenderer
import com.intellij.ui.layout.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.ecr.EcrClient
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.ExplorerDataKeys
import software.aws.toolkits.jetbrains.services.ecr.EcrPushRequest
import software.aws.toolkits.jetbrains.services.ecr.EcrRepositoryNode
import software.aws.toolkits.jetbrains.services.ecr.getDockerLogin
import software.aws.toolkits.jetbrains.services.ecr.getDockerServerRuntimeInstance
import software.aws.toolkits.jetbrains.services.ecr.pushImage
import software.aws.toolkits.jetbrains.services.ecr.resources.EcrResources
import software.aws.toolkits.jetbrains.services.ecr.resources.Repository
import software.aws.toolkits.jetbrains.services.ecr.toLocalImageList
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.ui.blankAsNull
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message

class PushTagToRepositoryAction :
    DumbAwareAction(),
    CoroutineScope by ApplicationThreadPoolScope("PushRepositoryAction") {
    private val dockerServerRuntime: Deferred<DockerServerRuntimeInstance> =
        async(start = CoroutineStart.LAZY) { getDockerServerRuntimeInstance() }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: EcrClient = project.awsClient()

        val selectedRepository = e.getData(ExplorerDataKeys.SELECTED_NODES)
            ?.mapNotNull { it as? EcrRepositoryNode }
            ?.takeIf { it.size == 1 }
            ?.first()
            ?.repository

        val dialog = PushToEcrDialog(project, selectedRepository, dockerServerRuntime)
        val result = dialog.showAndGet()

        if (!result) {
            // user cancelled; noop
            return
        }
        val pushRequest = dialog.getPushRequest()

        launch {
            try {
                val authData = withContext(Dispatchers.IO) {
                    client.authorizationToken.authorizationData().first()
                }

                val ecrLogin = authData.getDockerLogin()
                pushImage(project, dockerServerRuntime.await(), ecrLogin, pushRequest)
            } catch (e: SdkException) {
                val message = message("ecr.push.credential_fetch_failed")

                LOG.error(e) { message }
                notifyError(message("ecr.push.title"), message)
            } catch (e: Exception) {
                val message = message("ecr.push.unknown_exception")

                LOG.error(e) { message }
                notifyError(message("ecr.push.title"), message)
            }
        }
    }

    companion object {
        private val LOG = getLogger<PushTagToRepositoryAction>()
    }
}

internal data class LocalImage(
    val imageId: String,
    val tag: String?
)

internal class PushToEcrDialog(
    project: Project,
    selectedRepository: Repository?,
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
        selectedRepository?.let { repo -> remoteRepos.selectedItem { it == repo } }

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
        // attach a group so combo boxes grow/shrink together
        val selectorGroup = "selectors"

        row(message("ecr.repo.label")) {
            component(remoteRepos)
                .sizeGroup(selectorGroup)
                .withErrorOnApplyIf(message("ecr.repo.not_selected")) { it.selected() == null }
        }

        row(message("ecr.push.source")) {
            // property binding syntax causes kotlin compiler error for some reason
            comboBox(
                localImageRepoTags,
                { localImage },
                { ::localImage.set(it) },
                listCellRenderer { value, _, _ ->
                    text = value.tag ?: value.imageId.take(15)
                }
            )
                .sizeGroup(selectorGroup)
                .withErrorOnApplyIf(message("ecr.image.not_selected")) { it.selected() == null }
        }

        row(message("ecr.push.remoteTag")) {
            textField(::remoteTag)
                .sizeGroup(selectorGroup)
                .withErrorOnApplyIf(message("ecr.tag.not_provided")) { it.blankAsNull() == null }
        }
    }

    fun getPushRequest() = EcrPushRequest(
        localImage?.imageId ?: throw IllegalStateException("image id was null"),
        remoteRepos.selected() ?: throw IllegalStateException("repository uri was null"),
        remoteTag
    )
}
