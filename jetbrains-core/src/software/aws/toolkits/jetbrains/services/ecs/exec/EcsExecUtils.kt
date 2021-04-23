// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

class EcsExecUtils(private val project: Project) : CoroutineScope by ApplicationThreadPoolScope("EcsExec") {
    fun executeCommandFlagStatus(service: Service): Boolean {
        val currentServiceState = checkServiceCompletion(service, project)
        return currentServiceState.enableExecuteCommand()
    }

    fun updateExecuteCommandFlag(service: Service, enabled: Boolean) {
        launch {
            val request = UpdateServiceRequest.builder()
                .cluster(service.clusterArn())
                .service(service.serviceName())
                .enableExecuteCommand(enabled)
                .forceNewDeployment(true).build()
            project.awsClient<EcsClient>().updateService(request)
        }
    }

    suspend fun checkServiceState(service: Service): Boolean {
        return try {
            do {
                delay(5000)
                val response = checkServiceCompletion(service, project)
            } while (response.deployments().first().rolloutStateAsString() != "COMPLETED")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkServiceCompletion(service: Service, project: Project): Service =
        AwsResourceCache.getInstance().getResourceNow(
            EcsResources.describeService(
                service.clusterArn(),
                service.serviceArn()
            ),
            project.activeRegion(), project.activeCredentialProvider(), useStale = false, forceFetch = true
        )
}
