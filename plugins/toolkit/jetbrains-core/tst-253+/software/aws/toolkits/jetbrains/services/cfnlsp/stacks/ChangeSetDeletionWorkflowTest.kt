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
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateStackActionResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeDeletionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionState
import java.util.concurrent.CompletableFuture

class ChangeSetDeletionWorkflowTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockClientService: CfnClientService
    private lateinit var workflow: ChangeSetDeletionWorkflow

    @Before
    fun setUp() {
        mockClientService = mock()
        workflow = ChangeSetDeletionWorkflow(projectRule.project, mockClientService)
    }

    @Test
    fun `returns Failed when deleteChangeSet returns null`() {
        whenever(mockClientService.deleteChangeSet(any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        val result = workflow.delete("test-stack", "changeset-1").get()

        assertThat(result).isInstanceOf(PollResult.Failed::class.java)
    }

    @Test
    fun `returns Success when deletion completes successfully`() {
        whenever(mockClientService.deleteChangeSet(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getChangeSetDeletionStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.DELETION_COMPLETE, StackActionState.SUCCESSFUL)
            )
        )

        val result = workflow.delete("test-stack", "changeset-1").get()

        assertThat(result).isInstanceOf(PollResult.Success::class.java)
    }

    @Test
    fun `returns Failed when deletion fails`() {
        whenever(mockClientService.deleteChangeSet(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getChangeSetDeletionStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.DELETION_FAILED, StackActionState.FAILED)
            )
        )
        whenever(mockClientService.describeChangeSetDeletionStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                DescribeDeletionStatusResult("id-1", StackActionPhase.DELETION_FAILED, StackActionState.FAILED, failureReason = "Access denied")
            )
        )

        val result = workflow.delete("test-stack", "changeset-1").get()

        assertThat(result).isInstanceOf(PollResult.Failed::class.java)
    }
}
