// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateStackActionResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeDeploymentStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionState
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewPanelTabber
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewTab
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewWindowManager
import java.util.concurrent.CompletableFuture

class DeploymentWorkflowTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockClientService: CfnClientService
    private lateinit var mockWindowManager: StackViewWindowManager
    private lateinit var mockTabber: StackViewPanelTabber
    private lateinit var workflow: DeploymentWorkflow

    @Before
    fun setUp() {
        mockClientService = mock()
        mockWindowManager = mock()
        mockTabber = mock()
        workflow = DeploymentWorkflow(projectRule.project, mockClientService, mockWindowManager)
    }

    @Test
    fun `returns Failed when createDeployment returns null`() {
        whenever(mockClientService.createDeployment(any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )
        whenever(mockWindowManager.getOrOpenTabber("test-stack")).thenReturn(mockTabber)

        val result = workflow.deploy("test-stack", "changeset-1").get()

        assertThat(result).isInstanceOf(PollResult.Failed::class.java)
        verify(mockTabber).switchToTab(StackViewTab.EVENTS)
        verify(mockTabber, never()).restartStatusPolling()
    }

    @Test
    fun `returns Success when deployment completes successfully`() {
        whenever(mockClientService.createDeployment(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getDeploymentStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.DEPLOYMENT_COMPLETE, StackActionState.SUCCESSFUL)
            )
        )
        whenever(mockWindowManager.getOrOpenTabber("test-stack")).thenReturn(mockTabber)

        val result = workflow.deploy("test-stack", "changeset-1").get()

        assertThat(result).isInstanceOf(PollResult.Success::class.java)
        verify(mockTabber).switchToTab(StackViewTab.EVENTS)
        verify(mockTabber).restartStatusPolling()
    }

    @Test
    fun `returns Failed when deployment fails`() {
        whenever(mockClientService.createDeployment(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getDeploymentStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.DEPLOYMENT_FAILED, StackActionState.FAILED)
            )
        )
        whenever(mockClientService.describeDeploymentStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                DescribeDeploymentStatusResult("id-1", StackActionPhase.DEPLOYMENT_FAILED, StackActionState.FAILED, failureReason = "Rollback")
            )
        )

        val result = workflow.deploy("test-stack", "changeset-1").get()

        assertThat(result).isInstanceOf(PollResult.Failed::class.java)
    }
}
