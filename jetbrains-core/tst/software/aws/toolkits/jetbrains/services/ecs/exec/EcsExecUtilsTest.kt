// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.Deployment
import software.amazon.awssdk.services.ecs.model.DeploymentRolloutState
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

class EcsExecUtilsTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManager = MockClientManagerRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private lateinit var client: EcsClient
    val clusterArn = "sample-cluster-arn-123"
    val serviceArn = "sample-service-arn-123"
    val ecsService = Service.builder()
        .clusterArn(clusterArn)
        .serviceArn(serviceArn)
        .enableExecuteCommand(true)
        .serviceName("service-name")
        .deployments(listOf(Deployment.builder().rolloutState(DeploymentRolloutState.IN_PROGRESS).build()))
        .build()

    @Before
    fun setup() {
        client = mockClientManager.create()
    }

    @Test
    fun `Correct message is displayed when enabling command execution is in progress and enabling is retried`() {
        val notificationMock = mock<Notifications>()
        projectRule.project.messageBus.connect(disposableRule.disposable).subscribe(Notifications.TOPIC, notificationMock)

        client.stub {
            on {
                describeServices(any<DescribeServicesRequest>())
            } doAnswer {
                DescribeServicesResponse.builder().services(ecsService).build()
            }
        }
        runBlocking {
            EcsExecUtils.ensureServiceIsInStableState(projectRule.project, ecsService, message("ecs.execute_command_enable")) {
                sampleFunction()
            }
            argumentCaptor<Notification>().apply {
                verify(notificationMock).notify(capture())
                assertThat(firstValue.content).contains(message("ecs.execute_command_enable_in_progress", ecsService.serviceName()))
            }
        }
    }

    @Test
    fun `Correct message is displayed when disabling command execution is in progress and running command is tried`() {
        val notificationMock = mock<Notifications>()
        projectRule.project.messageBus.connect(disposableRule.disposable).subscribe(Notifications.TOPIC, notificationMock)

        client.stub {
            on {
                describeServices(any<DescribeServicesRequest>())
            } doAnswer {
                DescribeServicesResponse.builder().services(ecsService).build()
            }
        }
        runBlocking {
            EcsExecUtils.ensureServiceIsInStableState(projectRule.project, ecsService, message("ecs.execute_command_run")) {
                sampleFunction()
            }

            argumentCaptor<Notification>().apply {
                verify(notificationMock).notify(capture())
                assertThat(firstValue.content).contains(message("ecs.execute_command_disable_in_progress", ecsService.serviceName()))
            }
        }
    }

    @Test
    fun `sampleFunction is invoked when service deployment state is not in progress`() {
        val sampleEcsService = Service.builder()
            .clusterArn(clusterArn)
            .serviceArn(serviceArn)
            .enableExecuteCommand(true)
            .serviceName("service-name")
            .deployments(listOf(Deployment.builder().rolloutState(DeploymentRolloutState.COMPLETED).build()))
            .build()
        val notificationMock = mock<Notifications>()
        projectRule.project.messageBus.connect(disposableRule.disposable).subscribe(Notifications.TOPIC, notificationMock)

        client.stub {
            on {
                describeServices(any<DescribeServicesRequest>())
            } doAnswer {
                DescribeServicesResponse.builder().services(sampleEcsService).build()
            }
        }
        runBlocking {
            EcsExecUtils.ensureServiceIsInStableState(projectRule.project, ecsService, message("ecs.execute_command_enable")) {
                sampleFunction()
            }
            argumentCaptor<Notification>().apply {
                verify(notificationMock).notify(capture())
                assertThat(firstValue.content).contains("Sample notification")
            }
        }
    }

    private fun sampleFunction() {
        notifyInfo("sampleNotification", "Sample notification", projectRule.project)
    }
}
