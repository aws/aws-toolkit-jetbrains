// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourceNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStatePurpose
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateResult
import java.util.concurrent.CompletableFuture

class ResourceStateServiceTest {

    @get:Rule
    val projectRule = ProjectRule()

    @Test
    fun `importResourceState calls LSP client with correct params`() {
        val mockClientService = mock<CfnClientService>()
        val stateService = ResourceStateService(projectRule.project)
        stateService.clientServiceProvider = { mockClientService }

        val mockResult = ResourceStateResult(
            successfulImports = mapOf("AWS::EC2::Instance" to listOf("test-ec2")),
            failedImports = emptyMap(),
            completionItem = null,
            warning = null
        )
        whenever(mockClientService.getResourceState(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        // Mock the editor to return valid values
        val mockEditor = mock<ResourceStateEditor>()
        whenever(mockEditor.getActiveEditor()).thenReturn(mock())
        whenever(mockEditor.getActiveDocumentUri()).thenReturn("file:///test.yaml")
        stateService.editor = mockEditor

        val resourceNode = mock<ResourceNode>()
        whenever(resourceNode.resourceType).thenReturn("AWS::EC2::Instance")
        whenever(resourceNode.resourceIdentifier).thenReturn("test-ec2")

        stateService.importResourceState(listOf(resourceNode))

        val paramsCaptor = argumentCaptor<ResourceStateParams>()
        verify(mockClientService).getResourceState(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.purpose).isEqualTo(ResourceStatePurpose.IMPORT.value)
        assertThat(paramsCaptor.firstValue.resourceSelections).hasSize(1)
        assertThat(paramsCaptor.firstValue.resourceSelections?.first()?.resourceType).isEqualTo("AWS::EC2::Instance")
        assertThat(paramsCaptor.firstValue.resourceSelections?.first()?.resourceIdentifiers).containsExactly("test-ec2")
    }

    @Test
    fun `cloneResourceState calls LSP client with correct params`() {
        val mockClientService = mock<CfnClientService>()
        val stateService = ResourceStateService(projectRule.project)
        stateService.clientServiceProvider = { mockClientService }

        val mockResult = ResourceStateResult(
            successfulImports = mapOf("AWS::S3::Bucket" to listOf("test-bucket")),
            failedImports = emptyMap(),
            completionItem = null,
            warning = null
        )
        whenever(mockClientService.getResourceState(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        // Mock the editor to return valid values
        val mockEditor = mock<ResourceStateEditor>()
        whenever(mockEditor.getActiveEditor()).thenReturn(mock())
        whenever(mockEditor.getActiveDocumentUri()).thenReturn("file:///test.yaml")
        stateService.editor = mockEditor

        val resourceNode = mock<ResourceNode>()
        whenever(resourceNode.resourceType).thenReturn("AWS::S3::Bucket")
        whenever(resourceNode.resourceIdentifier).thenReturn("test-bucket")

        stateService.cloneResourceState(listOf(resourceNode))

        val paramsCaptor = argumentCaptor<ResourceStateParams>()
        verify(mockClientService).getResourceState(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.purpose).isEqualTo(ResourceStatePurpose.CLONE.value)
    }

    @Test
    fun `getStackManagementInfo calls LSP client`() {
        val mockClientService = mock<CfnClientService>()
        val stateService = ResourceStateService(projectRule.project)
        stateService.clientServiceProvider = { mockClientService }

        val mockResult = ResourceStackManagementResult(
            physicalResourceId = "test-ec2",
            managedByStack = true,
            stackName = "test-stack",
            stackId = "arn:aws:cloudformation:us-east-1:123456789012:stack/test-stack/12345"
        )
        whenever(mockClientService.getStackManagementInfo(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        val resourceNode = mock<ResourceNode>()
        whenever(resourceNode.resourceIdentifier).thenReturn("test-ec2")

        stateService.getStackManagementInfo(resourceNode)

        verify(mockClientService).getStackManagementInfo("test-ec2")
    }
}
