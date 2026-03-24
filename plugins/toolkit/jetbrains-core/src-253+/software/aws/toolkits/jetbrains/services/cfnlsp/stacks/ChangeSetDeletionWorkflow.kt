// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DeleteChangeSetParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewWindowManager
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class ChangeSetDeletionWorkflow(
    project: Project,
    private val clientService: CfnClientService = CfnClientService.getInstance(project),
) : PollingWorkflow(project) {

    override val operationTitle = message("cloudformation.changeset.deletion.title")

    private lateinit var stackName: String
    private lateinit var changeSetName: String

    override fun fetchStatus(id: String): CompletableFuture<GetStackActionStatusResult?> =
        clientService.getChangeSetDeletionStatus(Identifiable(id))

    override fun handleTerminalState(status: GetStackActionStatusResult, id: String): CompletableFuture<PollResult<*>?> =
        when (status.phase) {
            StackActionPhase.DELETION_COMPLETE -> {
                notifyInfo(operationTitle, message("cloudformation.changeset.deletion.success", changeSetName, stackName), project = project)
                ChangeSetsManager.getInstance(project).refreshChangeSets(stackName)
                runInEdt {
                    StackViewWindowManager.getInstance(project)
                        .getTabberByName(stackName)?.removeChangeSetTab()
                }
                CompletableFuture.completedFuture(PollResult.Success(true))
            }

            StackActionPhase.DELETION_FAILED -> {
                clientService.describeChangeSetDeletionStatus(Identifiable(id)).thenApply { details ->
                    notifyError(
                        operationTitle,
                        message("cloudformation.changeset.deletion.failed", changeSetName, stackName, details?.failureReason ?: "Unknown"),
                        project = project
                    )
                    PollResult.Failed(details?.failureReason)
                }
            }

            else -> CompletableFuture.completedFuture(null)
        }

    fun delete(stackName: String, changeSetName: String): CompletableFuture<PollResult<Boolean>> {
        this.stackName = stackName
        this.changeSetName = changeSetName
        val id = UUID.randomUUID().toString()

        return clientService.deleteChangeSet(DeleteChangeSetParams(id, changeSetName, stackName)).thenCompose { result ->
            if (result == null) {
                notifyError(
                    operationTitle,
                    message("cloudformation.changeset.deletion.failed", changeSetName, stackName, "Failed to start deletion"),
                    project = project
                )
                CompletableFuture.completedFuture(PollResult.Failed("Failed to start deletion"))
            } else {
                notifyInfo(operationTitle, message("cloudformation.changeset.deletion.started", changeSetName, stackName), project = project)
                poll(id)
            }
        }
    }
}
