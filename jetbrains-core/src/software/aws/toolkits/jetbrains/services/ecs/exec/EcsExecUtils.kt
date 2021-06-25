// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DeploymentRolloutState
import software.amazon.awssdk.services.ecs.model.DescribeContainerInstancesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest
import software.amazon.awssdk.services.ecs.model.InvalidParameterException
import software.amazon.awssdk.services.ecs.model.LaunchType
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.Task as EcsTask
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.GetInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.PolicyEvaluationDecisionType
import software.amazon.awssdk.services.iam.model.SimulatePrincipalPolicyRequest
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager.Companion.getConnectionSettings
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import software.aws.toolkits.jetbrains.core.getResourceNow
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.utils.notifyWarn
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.EcsTelemetry
import software.aws.toolkits.telemetry.Result

object EcsExecUtils : CoroutineScope by ApplicationThreadPoolScope("EcsExec") {
    private const val SESSION_MANAGER_CREATE_CONTROL_CHANNEL_PERMISSION = "ssmmessages:CreateControlChannel"
    private const val SESSION_MANAGER_CREATE_DATA_CHANNEL_PERMISSION = "ssmmessages:CreateDataChannel"
    private const val SESSION_MANAGER_OPEN_CONTROL_CHANNEL_PERMISSION = "ssmmessages:OpenControlChannel"
    private const val SESSION_MANAGER_OPEN_DATA_CHANNEL_PERMISSION = "ssmmessages:OpenDataChannel"
    fun updateExecuteCommandFlag(project: Project, service: Service, enabled: Boolean) {
        if (ensureServiceIsInStableState(project, service)) {
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
        } else {
            if (enabled) {
                notifyWarn(message("ecs.execute_command_enable"), message("ecs.execute_command_enable_in_progress", service.serviceName()), project)
            } else {
                notifyWarn(message("ecs.execute_command_disable"), message("ecs.execute_command_disable_in_progress", service.serviceName()), project)
            }
        }
    }

    private fun checkServiceState(project: Project, service: Service, enable: Boolean) {
        val title = if (enable) {
            message("ecs.execute_command_enable_progress_indicator_message", service.serviceName())
        } else {
            message("ecs.execute_command_disable_progress_indicator_message", service.serviceName())
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

    private fun getEc2InstanceTaskRoleArn(project: Project, clusterArn: String, ecsClient: EcsClient, task: EcsTask): String? {
        try {
            val iamClient = project.awsClient<IamClient>()
            val containerInstanceArn = task.containerInstanceArn()
            val res = ecsClient.describeContainerInstances(
                DescribeContainerInstancesRequest
                    .builder()
                    .cluster(clusterArn)
                    .containerInstances(containerInstanceArn).build()
            )
            val ec2InstanceId = res.containerInstances().first().ec2InstanceId()
            val instanceProfileArn = project.awsClient<Ec2Client>().describeInstances(
                DescribeInstancesRequest.builder().instanceIds(ec2InstanceId).build()
            ).reservations().first().instances().first().iamInstanceProfile().arn() ?: return null
            val instanceProfileName = instanceProfileArn.substringAfter(":instance-profile/")
            return iamClient.getInstanceProfile(GetInstanceProfileRequest.builder().instanceProfileName(instanceProfileName).build()).instanceProfile()
                .roles().first().arn() ?: null
        } catch (e: Exception) {
            return null
        }
    }

    fun getTaskRoleArn(project: Project, clusterArn: String, taskArn: String): String? {
        val ecsClient = project.awsClient<EcsClient>()
        val task = ecsClient.describeTasks(DescribeTasksRequest.builder().tasks(taskArn).cluster(clusterArn).build()).tasks().first()
        return if (task.overrides().taskRoleArn() != null) {
            task.overrides().taskRoleArn()
        } else {
            val roleArn = project.getResourceNow(EcsResources.describeTaskDefinition(task.taskDefinitionArn())).taskRoleArn()
            if (roleArn == null) {
                val launchType = task.launchType()
                if (launchType == LaunchType.EC2) {
                    getEc2InstanceTaskRoleArn(project, clusterArn, ecsClient, task)
                } else {
                    return null
                }
            } else {
                roleArn
            }
        }
    }

    fun checkRequiredPermissions(project: Project, clusterArn: String, taskArn: String): Boolean {
        try {
            val iamClient = project.awsClient<IamClient>()
            val taskRoleArn = getTaskRoleArn(project, clusterArn, taskArn) ?: return false
            val permissions = listOf(
                SESSION_MANAGER_CREATE_CONTROL_CHANNEL_PERMISSION,
                SESSION_MANAGER_CREATE_DATA_CHANNEL_PERMISSION,
                SESSION_MANAGER_OPEN_CONTROL_CHANNEL_PERMISSION,
                SESSION_MANAGER_OPEN_DATA_CHANNEL_PERMISSION
            )


            val response = iamClient.simulatePrincipalPolicy{
                it.policySourceArn(taskRoleArn).actionNames(permissions)
            }
            val permissionResults = response.evaluationResults().map { it.evalDecision().name }
            for (permission in permissionResults) {
                if (permission != PolicyEvaluationDecisionType.ALLOWED.name) {
                    return false
                }
            }
        } catch (e: Exception) {
            notifyWarn(message("ecs.execute_command_permissions_required_title"), message("ecs.execute_command_permissions_not_verified"))
        }
        return true
    }

    fun ensureServiceIsInStableState(project: Project, service: Service): Boolean {
        val response = project.awsClient<EcsClient>().describeServices(
            DescribeServicesRequest.builder()
                .cluster(service.clusterArn())
                .services(service.serviceArn()).build()
        )
        val serviceStateChangeInProgress = response.services().first().deployments().first().rolloutState() == DeploymentRolloutState.IN_PROGRESS
        return !serviceStateChangeInProgress
    }
}
