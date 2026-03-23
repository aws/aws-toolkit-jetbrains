// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateDeploymentParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionState
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewTab
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewWindowManager
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class DeploymentWorkflow(
    project: Project,
    private val clientService: CfnClientService = CfnClientService.getInstance(project),
    private val windowManager: StackViewWindowManager = StackViewWindowManager.getInstance(project),
) : PollingWorkflow(project) {

    override val operationTitle = message("cloudformation.deployment.title")

    private lateinit var stackName: String

    override fun fetchStatus(id: String): CompletableFuture<GetStackActionStatusResult?> =
        clientService.getDeploymentStatus(Identifiable(id))

    override fun handleTerminalState(status: GetStackActionStatusResult, id: String): CompletableFuture<PollResult<*>?> =
        when (status.phase) {
            StackActionPhase.DEPLOYMENT_COMPLETE -> {
                StacksManager.getInstance(project).reload()
                ChangeSetsManager.getInstance(project).refreshChangeSets(stackName)
                if (status.state == StackActionState.SUCCESSFUL) {
                    notifyInfo(operationTitle, message("cloudformation.deployment.success", stackName), project = project)
                    CompletableFuture.completedFuture(PollResult.Success(true))
                } else {
                    clientService.describeDeploymentStatus(Identifiable(id)).thenApply { details ->
                        notifyError(
                            operationTitle,
                            message("cloudformation.deployment.failed", stackName, details?.failureReason ?: "Unknown"),
                            project = project
                        )
                        PollResult.Failed(details?.failureReason)
                    }
                }
            }

            StackActionPhase.DEPLOYMENT_FAILED, StackActionPhase.VALIDATION_FAILED -> {
                StacksManager.getInstance(project).reload()
                ChangeSetsManager.getInstance(project).refreshChangeSets(stackName)
                clientService.describeDeploymentStatus(Identifiable(id)).thenApply { details ->
                    notifyError(operationTitle, message("cloudformation.deployment.failed", stackName, details?.failureReason ?: "Unknown"), project = project)
                    PollResult.Failed(details?.failureReason)
                }
            }

            else -> CompletableFuture.completedFuture(null)
        }

    fun deploy(stackName: String, changeSetName: String): CompletableFuture<PollResult<Boolean>> {
        this.stackName = stackName
        val id = UUID.randomUUID().toString()
        val statusHandle = CfnOperationStatusService.getInstance(project)
            .acquire(stackName, OperationType.DEPLOYMENT, changeSetName)

        // Open stack view BEFORE starting deployment to avoid EDT blocking during deployment
        val tabber = try {
            windowManager.getOrOpenTabber(stackName)
        } catch (e: Exception) {
            LOG.error(e) { "Failed to open stack view for $stackName before deployment" }
            null
        }

        tabber?.switchToTab(StackViewTab.EVENTS)

        return clientService.createDeployment(CreateDeploymentParams(id, changeSetName, stackName)).thenCompose { result ->
            if (result == null) {
                notifyError(operationTitle, message("cloudformation.deployment.failed", stackName, "Failed to start deployment"), project = project)
                statusHandle.update(StackActionPhase.DEPLOYMENT_FAILED)
                statusHandle.release()
                CompletableFuture.completedFuture(PollResult.Failed("Failed to start deployment"))
            } else {
                notifyInfo(operationTitle, message("cloudformation.deployment.started", stackName), project = project)

                tabber?.restartStatusPolling()

                poll<Boolean>(id).whenComplete { pollResult, _ ->
                    val phase = when (pollResult) {
                        is PollResult.Success -> StackActionPhase.DEPLOYMENT_COMPLETE
                        else -> StackActionPhase.DEPLOYMENT_FAILED
                    }
                    statusHandle.update(phase)
                    statusHandle.release()
                }
            }
        }
    }

    companion object {
        private val LOG = getLogger<DeploymentWorkflow>()
    }
}
