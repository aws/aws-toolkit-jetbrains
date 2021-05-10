// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.amazon.awssdk.core.waiters.WaiterResponse
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

object EcsExecUtils : CoroutineScope by ApplicationThreadPoolScope("EcsExec") {
    fun updateExecuteCommandFlag(project: Project, service: Service, enabled: Boolean) {
        launch {
            val request = UpdateServiceRequest.builder()
                .cluster(service.clusterArn())
                .service(service.serviceName())
                .enableExecuteCommand(enabled)
                .forceNewDeployment(true).build()
            project.awsClient<EcsClient>().updateService(request)
        }
    }

    suspend fun checkServiceState(project: Project, service: Service): Boolean {
        delay(5000)
        val request = DescribeServicesRequest.builder().cluster(service.clusterArn()).services(service.serviceArn()).build()
        val client = project.awsClient<EcsClient>()
        val waiter = client.waiter()
        val waiterResponse: WaiterResponse<DescribeServicesResponse> = waiter.waitUntilServicesStable(request)
        if (waiterResponse.matched().response().isPresent) { return true }
        return false
    }
}
