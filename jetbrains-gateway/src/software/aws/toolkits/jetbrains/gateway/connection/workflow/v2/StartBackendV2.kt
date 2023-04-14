// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.gateway.connection.workflow.v2

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.rd.util.startLongBackgroundAsync
import com.intellij.openapi.rd.util.startUnderModalProgressAsync
import com.intellij.remote.RemoteCredentialsHolder
import com.jetbrains.gateway.ssh.ClientOverSshTunnelConnector
import com.jetbrains.gateway.ssh.DeployFlowUtil
import com.jetbrains.gateway.ssh.HostDeployInputs
import com.jetbrains.gateway.ssh.SshHostTunnelConnector
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.gateway.ssh.deploy.DeployTargetInfo
import com.jetbrains.gateway.ssh.deploy.LoggingHostCommandExecutorWrapper
import com.jetbrains.gateway.ssh.deploy.executeCommand
import com.jetbrains.rd.util.lifetime.Lifetime
import software.aws.toolkits.jetbrains.gateway.WorkspaceIdentifier
import software.aws.toolkits.jetbrains.gateway.connection.caws.CawsSshConnectionConfigModifier
import software.aws.toolkits.jetbrains.services.caws.CawsConstants
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.Step
import software.aws.toolkits.jetbrains.utils.execution.steps.StepEmitter
import software.aws.toolkits.resources.message

class StartBackendV2(
    private val lifetime: Lifetime,
    private val indicator: ProgressIndicator,
    private val identifier: WorkspaceIdentifier
) : Step() {
    override val stepName: String = message("gateway.connection.workflow.start_ide")

    override fun execute(context: Context, stepEmitter: StepEmitter, ignoreCancellation: Boolean) {
        val creds = RemoteCredentialsHolder().apply {
            setHost("${CawsSshConnectionConfigModifier.HOST_PREFIX}${identifier.friendlyString}")
        }
        val executor = CawsHostCommandExecutor(creds)

        lifetime.startLongBackgroundAsync {
            lifetime.startUnderModalProgressAsync("Connecting to dev environment", true) {
                val hostinfo = HostDeployInputs.WithHostInfo(LoggingHostCommandExecutorWrapper(executor))
                val accessor = DeployFlowUtil.checkHostAndDeployWorker(indicator, SshMultistagePanelContext(hostinfo))
                    ?: return@startUnderModalProgressAsync null

                // force-hide meaningless 'upgrade' prompt for user
                hostinfo.loggingCommandExecutor.executeCommand(
                    lifetime,
                    "sudo", "sh", "-c", "mkdir -p /etc/xdg/JetBrains/RemoteDev/ && echo 'true' > /etc/xdg/JetBrains/RemoteDev/disableManualDeployment"
                )

                val deployData = hostinfo.upgrade(accessor)
                    .upgrade(
                        CawsConstants.CAWS_ENV_PROJECT_DIR,
                        DeployTargetInfo.NoDeploy(CawsConstants.CAWS_ENV_IDE_BACKEND_DIR, null)
                    )

                DeployFlowUtil.fullDeployCycle(lifetime, deployData)
            }
        }
    }
}
