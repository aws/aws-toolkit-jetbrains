// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourceNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceResult
import java.util.concurrent.CompletableFuture

class ResourcesManagerTest {

    @get:Rule
    val projectRule = ProjectRule()

    @Test
    fun `initially has no cached resources`() {
        val manager = ResourcesManager(projectRule.project)

        assertThat(manager.getCachedResources("AWS::EC2::Instance")).isNull()
        assertThat(manager.isLoaded("AWS::EC2::Instance")).isFalse()
    }

    @Test
    fun `reload sends request to LSP server`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        val mockResult = mock<ListResourcesResult>()
        whenever(mockClientService.listResources(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        manager.clientServiceProvider = { mockClientService }
        manager.clear("AWS::EC2::Instance")

        manager.reload("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        val paramsCaptor = argumentCaptor<ListResourcesParams>()
        verify(mockClientService).listResources(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.resources).hasSize(1)
        assertThat(paramsCaptor.firstValue.resources?.first()?.resourceType).isEqualTo("AWS::EC2::Instance")
        assertThat(paramsCaptor.firstValue.resources?.first()?.nextToken).isNull()
    }

    @Test
    fun `clear resets state`() {
        val manager = ResourcesManager(projectRule.project)

        manager.clear("AWS::EC2::Instance")

        assertThat(manager.isLoaded("AWS::EC2::Instance")).isFalse()
        assertThat(manager.getCachedResources("AWS::EC2::Instance")).isNull()
    }

    @Test
    fun `searchResource adds found resource to cache`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        val mockResourceSummary = ResourceSummary("AWS::EC2::Instance", listOf("testResource"))
        val mockResult = SearchResourceResult(found = true, resource = mockResourceSummary)
        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        manager.clientServiceProvider = { mockClientService }

        assertThat(manager.getCachedResources("AWS::EC2::Instance")).isNull()

        val result = manager.searchResource("AWS::EC2::Instance", "testResource")
        testScheduler.advanceUntilIdle()
        assertThat(result.get()).isTrue()

        val paramsCaptor = argumentCaptor<SearchResourceParams>()
        verify(mockClientService).searchResource(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.resourceType).isEqualTo("AWS::EC2::Instance")
        assertThat(paramsCaptor.firstValue.identifier).isEqualTo("testResource")

        // Resource should now be in cache
        val cachedResources = manager.getResourceIdentifiers("AWS::EC2::Instance")
        assertThat(cachedResources).contains("testResource")
        assertThat(manager.isLoaded("AWS::EC2::Instance")).isTrue()
    }

    @Test
    fun `addListener adds listener to list`() {
        val manager = ResourcesManager(projectRule.project)
        var notificationReceived = false
        val listener: ResourcesChangeListener = { _, _ -> notificationReceived = true }

        manager.addListener(listener)
        manager.clear("AWS::EC2::Instance")

        assertThat(notificationReceived).isTrue()
    }

    @Test
    fun `getResourceIdentifiers returns empty list for unknown type`() {
        val manager = ResourcesManager(projectRule.project)

        val result = manager.getResourceIdentifiers("AWS::Unknown::Type")

        assertThat(result).isEmpty()
    }

    @Test
    fun `hasMore returns false for unknown type`() {
        val manager = ResourcesManager(projectRule.project)

        val result = manager.hasMore("AWS::EC2::Instance")

        assertThat(result).isFalse()
    }

    @Test
    fun `getLoadedResourceTypes returns empty set initially`() {
        val manager = ResourcesManager(projectRule.project)

        val result = manager.getLoadedResourceTypes()

        assertThat(result).isEmpty()
    }

    @Test
    fun `loadMoreResources does nothing when no nextToken`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)
        manager.clientServiceProvider = { mockClientService }

        val mockResult = mock<ListResourcesResult>()
        val mockResourceSummary = mock<ResourceSummary>()
        whenever(mockResourceSummary.typeName).thenReturn("AWS::EC2::Instance")
        whenever(mockResourceSummary.resourceIdentifiers).thenReturn(listOf("instance-1"))
        whenever(mockResourceSummary.nextToken).thenReturn(null)
        whenever(mockResult.resources).thenReturn(listOf(mockResourceSummary))
        whenever(mockClientService.listResources(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        manager.reload("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        // Reset mock to verify no additional calls
        org.mockito.kotlin.reset(mockClientService)

        manager.loadMoreResources("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        // Should not make any LSP calls since no nextToken
        verify(mockClientService, never()).listResources(any())
    }

    @Test
    fun `clear with null clears all resource types`() {
        val manager = ResourcesManager(projectRule.project)
        var ec2Cleared = false
        var s3Cleared = false

        manager.addListener { resourceType, resources ->
            when (resourceType) {
                "AWS::EC2::Instance" -> ec2Cleared = resources.isEmpty()
                "AWS::S3::Bucket" -> s3Cleared = resources.isEmpty()
            }
        }

        manager.clear("AWS::EC2::Instance") // Add some state
        manager.clear("AWS::S3::Bucket") // Add some state

        manager.clear(null)

        assertThat(ec2Cleared).isTrue()
        assertThat(s3Cleared).isTrue()
    }

    @Test
    fun `searchResource handles exception gracefully`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.failedFuture(RuntimeException("Test exception")))
        manager.clientServiceProvider = { mockClientService }

        val result = manager.searchResource("AWS::EC2::Instance", "testResource")
        testScheduler.advanceUntilIdle()

        assertThat(result.get()).isFalse()
    }

    @Test
    fun `searchResource returns false when resource not found`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        val mockResult = mock<SearchResourceResult>()
        whenever(mockResult.found).thenReturn(false)
        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.completedFuture(mockResult))
        manager.clientServiceProvider = { mockClientService }

        val result = manager.searchResource("AWS::EC2::Instance", "testResource")
        testScheduler.advanceUntilIdle()

        assertThat(result.get()).isFalse()

        val paramsCaptor = argumentCaptor<SearchResourceParams>()
        verify(mockClientService).searchResource(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.resourceType).isEqualTo("AWS::EC2::Instance")
        assertThat(paramsCaptor.firstValue.identifier).isEqualTo("testResource")
    }

    @Test
    fun `searchResource reloads type when not loaded and resource found without data`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        val mockSearchResult = mock<SearchResourceResult>()
        whenever(mockSearchResult.found).thenReturn(true)
        whenever(mockSearchResult.resource).thenReturn(null)

        val mockListResult = mock<ListResourcesResult>()
        whenever(mockListResult.resources).thenReturn(emptyList())

        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.completedFuture(mockSearchResult))
        whenever(mockClientService.listResources(any())).thenReturn(CompletableFuture.completedFuture(mockListResult))

        manager.clientServiceProvider = { mockClientService }

        manager.searchResource("AWS::EC2::Instance", "testResource")
        testScheduler.advanceUntilIdle()

        // Search and reload calls
        verify(mockClientService).searchResource(any())
        verify(mockClientService).listResources(any())
    }

    @Test
    fun `getStackManagementInfo handles successful response`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        val mockResult = ResourceStackManagementResult(
            physicalResourceId = "testPhysicalResourceId",
            managedByStack = true,
            stackName = "testStackName",
            stackId = "testStackId"
        )
        whenever(mockClientService.getStackManagementInfo(any())).thenReturn(CompletableFuture.completedFuture(mockResult))
        manager.clientServiceProvider = { mockClientService }

        val resourceNode = mock<ResourceNode>()
        whenever(resourceNode.resourceIdentifier).thenReturn("testPhysicalResourceId")

        manager.getStackManagementInfo(resourceNode)
        testScheduler.advanceUntilIdle()

        verify(mockClientService).getStackManagementInfo("testPhysicalResourceId")
    }

    @Test
    fun `getStackManagementInfo handles exception`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourcesManager(projectRule.project)

        whenever(mockClientService.getStackManagementInfo(any())).thenReturn(CompletableFuture.failedFuture(RuntimeException("Test exception")))
        manager.clientServiceProvider = { mockClientService }

        val resourceNode = mock<ResourceNode>()
        whenever(resourceNode.resourceIdentifier).thenReturn("testPhysicalResourceId")

        manager.getStackManagementInfo(resourceNode)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `dispose clears listeners`() {
        val manager = ResourcesManager(projectRule.project)
        var notificationReceived = false
        val listener: ResourcesChangeListener = { _, _ -> notificationReceived = true }

        manager.addListener(listener)
        manager.dispose()
        manager.clear("AWS::EC2::Instance")

        // Should not receive notification after dispose
        assertThat(notificationReceived).isFalse()
    }
}
