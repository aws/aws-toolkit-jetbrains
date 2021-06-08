// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.InvalidParameterException
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager.Companion.getConnectionSettings
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.EcsTelemetry
import software.aws.toolkits.telemetry.Result

object EcsExecUtils : CoroutineScope by ApplicationThreadPoolScope("EcsExec") {
    fun updateExecuteCommandFlag(project: Project, service: Service, enabled: Boolean) {
        launch {
            try {
                val request = UpdateServiceRequest.builder()
                    .cluster(service.clusterArn())
                    .service(service.serviceName())
                    .enableExecuteCommand(enabled)
                    .forceNewDeployment(true).build()
                project.awsClient<EcsClient>().updateService(request)
                checkServiceState(project, service, enabled)
            } catch (e: InvalidParameterException) {
                runInEdt {
                    TaskRoleNotFoundWarningDialog(project).show()
                    EcsTelemetry.enableExecuteCommand(project, Result.Failed)
                }
            }
        }
    }

    private fun checkServiceState(project: Project, service: Service, enable: Boolean) {
        val title = if (enable) {
            message("ecs.execute_command_enable_progress_indicator_message")
        } else {
            message("ecs.execute_command_disable_progress_indicator_message")
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, title, false) {
                override fun run(indicator: ProgressIndicator) {
                    val request = DescribeServicesRequest.builder().cluster(service.clusterArn()).services(service.serviceArn()).build()
                    val client = project.awsClient<EcsClient>()
                    val waiter = client.waiter()
                    waiter.waitUntilServicesStable(request)
                }

                override fun onSuccess() {
                    val currentConnectionSettings = project.getConnectionSettings()
                    project.refreshAwsTree(EcsResources.describeService(service.clusterArn(), service.serviceArn()), currentConnectionSettings)

                    if (enable) {
                        notifyInfo(message("ecs.execute_command_enable"), message("ecs.execute_command_enable_success", service.serviceName()))
                        EcsTelemetry.enableExecuteCommand(project, Result.Succeeded)
                    } else {
                        notifyInfo(message("ecs.execute_command_disable"), message("ecs.execute_command_disable_success", service.serviceName()))
                        EcsTelemetry.disableExecuteCommand(project, Result.Succeeded)
                    }
                }

                override fun onThrowable(error: Throwable) {
                    if (enable) {
                        notifyError(message("ecs.execute_command_enable"), message("ecs.execute_command_enable_failed", service.serviceName()))
                        EcsTelemetry.enableExecuteCommand(project, Result.Failed)
                    } else {
                        notifyError(message("ecs.execute_command_disable"), message("ecs.execute_command_disable_failed", service.serviceName()))
                        EcsTelemetry.disableExecuteCommand(project, Result.Failed)
                    }
                }
            }
        )
    }
}
