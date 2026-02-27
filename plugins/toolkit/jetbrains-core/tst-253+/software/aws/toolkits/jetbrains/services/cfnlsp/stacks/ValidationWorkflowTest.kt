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
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeValidationStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionState
import java.util.concurrent.CompletableFuture

class ValidationWorkflowTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockClientService: CfnClientService
    private lateinit var workflow: ValidationWorkflow

    @Before
    fun setUp() {
        mockClientService = mock()
        workflow = ValidationWorkflow(projectRule.project, mockClientService)
    }

    @Test
    fun `returns Failed when createValidation returns null`() {
        whenever(mockClientService.createValidation(any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        val result = workflow.validate(createParams()).get()

        assertThat(result).isInstanceOf(ValidationResult.Failed::class.java)
        assertThat((result as ValidationResult.Failed).reason).isEqualTo("Failed to start validation")
    }

    @Test
    fun `returns Success when validation completes successfully`() {
        whenever(mockClientService.createValidation(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getValidationStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.VALIDATION_COMPLETE, StackActionState.SUCCESSFUL, emptyList())
            )
        )
        whenever(mockClientService.describeValidationStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                DescribeValidationStatusResult("id-1", StackActionPhase.VALIDATION_COMPLETE, StackActionState.SUCCESSFUL)
            )
        )

        val result = workflow.validate(createParams()).get()

        assertThat(result).isInstanceOf(ValidationResult.Success::class.java)
        val success = result as ValidationResult.Success
        assertThat(success.changeSetName).isEqualTo("changeset-1")
    }

    @Test
    fun `returns Failed when validation completes with failure state`() {
        whenever(mockClientService.createValidation(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getValidationStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.VALIDATION_COMPLETE, StackActionState.FAILED)
            )
        )
        whenever(mockClientService.describeValidationStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                DescribeValidationStatusResult("id-1", StackActionPhase.VALIDATION_COMPLETE, StackActionState.FAILED, failureReason = "Template error")
            )
        )

        val result = workflow.validate(createParams()).get()

        assertThat(result).isInstanceOf(ValidationResult.Failed::class.java)
        assertThat((result as ValidationResult.Failed).reason).isEqualTo("Template error")
    }

    @Test
    fun `returns Failed when phase is VALIDATION_FAILED`() {
        whenever(mockClientService.createValidation(any())).thenReturn(
            CompletableFuture.completedFuture(CreateStackActionResult("id-1", "changeset-1", "test-stack"))
        )
        whenever(mockClientService.getValidationStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                GetStackActionStatusResult("id-1", StackActionPhase.VALIDATION_FAILED, StackActionState.FAILED)
            )
        )
        whenever(mockClientService.describeValidationStatus(any())).thenReturn(
            CompletableFuture.completedFuture(
                DescribeValidationStatusResult("id-1", StackActionPhase.VALIDATION_FAILED, StackActionState.FAILED, failureReason = "Syntax error")
            )
        )

        val result = workflow.validate(createParams()).get()

        assertThat(result).isInstanceOf(ValidationResult.Failed::class.java)
        assertThat((result as ValidationResult.Failed).reason).isEqualTo("Syntax error")
    }

    private fun createParams() = CreateValidationParams(
        id = "test-id",
        uri = "file:///test.yaml",
        stackName = "test-stack",
        keepChangeSet = true
    )
}
