// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.stub
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.Deployment
import software.amazon.awssdk.services.ecs.model.DeploymentRolloutState
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service
import software.aws.toolkits.jetbrains.core.MockClientManagerRule

class EcsExecUtilsTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManager = MockClientManagerRule()

    private lateinit var client: EcsClient
    val clusterArn = "sample-cluster-arn-123"
    val serviceArn = "sample-service-arn-123"

    @Before
    fun setup() {
        client = mockClientManager.create()
    }

    @Test
    fun `Service update in progress returns false`() {
        val ecsService = Service.builder()
            .clusterArn(clusterArn)
            .serviceArn(serviceArn)
            .enableExecuteCommand(true)
            .serviceName("service-name")
            .deployments(listOf(Deployment.builder().rolloutState(DeploymentRolloutState.IN_PROGRESS).build()))
            .build()
        client.stub {
            on {
                describeServices(any<DescribeServicesRequest>())
            } doAnswer {
                DescribeServicesResponse.builder().services(ecsService).build()
            }
        }
        val serviceStateStable = runBlocking {
            EcsExecUtils.ensureServiceIsInStableState(projectRule.project, ecsService)
        }
        assertThat(serviceStateStable).isFalse
    }

    @Test
    fun `Service is currently stable returns true`() {
        val ecsService = Service.builder()
            .clusterArn(clusterArn)
            .serviceArn(serviceArn)
            .enableExecuteCommand(true)
            .serviceName("service-name")
            .deployments(listOf(Deployment.builder().rolloutState(DeploymentRolloutState.COMPLETED).build()))
            .build()
        client.stub {
            on {
                describeServices(any<DescribeServicesRequest>())
            } doAnswer {
                DescribeServicesResponse.builder().services(ecsService).build()
            }
        }
        val serviceStateStable = runBlocking {
            EcsExecUtils.ensureServiceIsInStableState(projectRule.project, ecsService)
        }
        assertThat(serviceStateStable).isTrue
    }
}
