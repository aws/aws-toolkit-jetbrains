// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr.actions

import com.intellij.docker.agent.DockerAgentProgressCallback
import com.intellij.docker.registry.DockerAgentRepositoryConfigImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.ecr.EcrClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.services.ecr.EcrRepositoryNode
import software.aws.toolkits.jetbrains.services.ecr.EcrUtils
import software.aws.toolkits.jetbrains.services.ecr.getDockerLogin
import software.aws.toolkits.jetbrains.services.ecr.resources.EcrResources
import software.aws.toolkits.jetbrains.services.ecr.resources.Repository
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message

class PullFromRepositoryAction : EcrDockerAction() {
    override val coroutineScope = ApplicationThreadPoolScope("PullFromRepositoryAction")

    override fun actionPerformed(selected: EcrRepositoryNode, e: AnActionEvent) {
        val dialog = PullFromRepositoryDialog(selected.repository, selected.nodeProject)
        val result = dialog.showAndGet()
        if (!result) {
            return
        }

        val (repo, image) = dialog.getPullRequest()
        val client: EcrClient = selected.nodeProject.awsClient()
        coroutineScope.launch {
            val runtime = dockerServerRuntime.await()
            val authData = withContext(Dispatchers.IO) {
                client.authorizationToken.authorizationData().first()
            }
            val ecrLogin = authData.getDockerLogin()
            val config = DockerAgentRepositoryConfigImpl(EcrUtils.buildDockerRepositoryModel(ecrLogin, repo, image))
            val progress = runtime.agent.pullImage(config, object : DockerAgentProgressCallback {
                override fun step(status: String, current: Long, total: Long) {
                    LOG.warn("status: $status, current: $current, total: $total")
                }

                override fun succeeded(message: String) {
                    LOG.warn("succeeded: $message")
                }

                override fun failed(message: String) {
                    LOG.warn("failed: $message")
                }
            })
            LOG.warn(progress.toString())
        }
    }

    private companion object {
        val LOG = getLogger<PullFromRepositoryAction>()
    }
}

private class PullFromRepositoryDialog(selectedRepository: Repository, project: Project) : DialogWrapper(project) {
    private val repoSelector = ResourceSelector.builder()
        .resource(EcrResources.LIST_REPOS)
        .customRenderer(SimpleListCellRenderer.create("") { it.repositoryName })
        .awsConnection(project)
        .build()

    private val imageSelector = ResourceSelector.builder()
        .resource {
            repoSelector.selected()?.repositoryName?.let { EcrResources.listTags(it) }
        }
        .customRenderer(SimpleListCellRenderer.create("") { it })
        .awsConnection(project)
        .build()

    init {
        repoSelector.selectedItem { it == selectedRepository }
        title = message("ecr.pull.title")
        setOKButtonText(message("ecr.pull.confirm"))

        init()
    }

    override fun createCenterPanel() = panel {
        row("repo") {
            repoSelector()
                .withErrorOnApplyIf(message("loading_resource.still_loading")) { it.isLoading }
                .withErrorOnApplyIf(message("ecr.repo.not_selected")) { it.selected() == null }

            repoSelector.addActionListener { imageSelector.reload() }
        }

        row("image") {
            imageSelector()
                .withErrorOnApplyIf(message("loading_resource.still_loading")) { it.isLoading }
                .withErrorOnApplyIf(message("ecr.image.not_selected")) { it.selected() == null }
        }
    }

    fun getPullRequest() = repoSelector.selected()!! to imageSelector.selected()!!
}
