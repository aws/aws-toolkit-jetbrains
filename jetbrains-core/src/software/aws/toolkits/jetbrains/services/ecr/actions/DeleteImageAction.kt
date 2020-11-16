// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.ImageIdentifier
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.actions.ExplorerNodeAction
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import software.aws.toolkits.jetbrains.services.ecr.EcrImageNode
import software.aws.toolkits.jetbrains.services.ecr.resources.EcrResources
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.EcrTelemetry
import software.aws.toolkits.telemetry.Result

class DeleteImageAction : ExplorerNodeAction<EcrImageNode>(message("ecr.delete.image.action", 0), null, AllIcons.Actions.Cancel) {
    override fun update(selected: List<EcrImageNode>, e: AnActionEvent) {
        // Only show up if the selected are part of one node
        e.presentation.isVisible = selected.map { it.parent.repositoryArn }.toSet().size == 1
        e.presentation.text = message("ecr.delete.image.action", selected.size)
    }

    override fun actionPerformed(selected: List<EcrImageNode>, e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        if (selected.isEmpty()) {
            return
        }
        val repository = selected.first().parent
        val response = Messages.showOkCancelDialog(
            project,
            message("ecr.delete.image.description", selected.size, repository.repositoryName),
            message("ecr.delete.image.action", selected.size, repository.repositoryName),
            message("s3.delete.object.delete"),
            Messages.getCancelButton(),
            Messages.getWarningIcon()
        )

        if (response != Messages.OK) {
            EcrTelemetry.deleteTags(project, Result.Cancelled, selected.size.toDouble())
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                message("ecr.delete.image.deleting"),
                false,
                ALWAYS_BACKGROUND
            ) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val client: EcrClient = project.awsClient()
                        client.batchDeleteImage {
                            it
                                .repositoryName(selected.first().parent.repositoryName)
                                .imageIds(
                                    selected.map { node ->
                                        ImageIdentifier.builder().imageTag(node.tag).build()
                                    }
                                )
                        }
                        notifyInfo(
                            project = project,
                            title = message("aws.notification.title"),
                            content = message("ecr.delete.image.succeeded", selected.size, repository.repositoryName)
                        )
                        project.refreshAwsTree(EcrResources.listTags(repository.repositoryName))
                        EcrTelemetry.deleteTags(project, Result.Succeeded, selected.size.toDouble())
                    } catch (e: Exception) {
                        LOG.error(e) { "Exception thrown while trying to delete ${selected.size} tags" }
                        notifyError(
                            project = project,
                            content = message("ecr.delete.image.failed", selected.size, repository.repositoryName)
                        )
                        EcrTelemetry.deleteTags(project, Result.Failed, selected.size.toDouble())
                    }
                }
            }
        )
    }

    private companion object {
        val LOG = getLogger<DeleteImageAction>()
    }
}
