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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceResult
import java.util.concurrent.CompletableFuture

class ResourceLoaderTest {

    @get:Rule
    val projectRule = ProjectRule()

    @Test
    fun `initially has no cached resources`() {
        val loader = ResourceLoader(projectRule.project)

        assertThat(loader.getCachedResources("AWS::EC2::Instance")).isNull()
        assertThat(loader.isLoaded("AWS::EC2::Instance")).isFalse()
        assertThat(loader.getResourceIdentifiers("AWS::EC2::Instance")).isEmpty()
        assertThat(loader.hasMore("AWS::EC2::Instance")).isFalse()
        assertThat(loader.getLoadedResourceTypes()).isEmpty()
    }

    @Test
    fun `refreshResources sends request to LSP server`() {
        val mockClientService = mock<CfnClientService>()
        val loader = ResourceLoader(projectRule.project)

        val mockResult = RefreshResourcesResult(
            resources = listOf(
                ResourceSummary("AWS::EC2::Instance", listOf("test-instance-1"), null)
            )
        )
        whenever(mockClientService.refreshResources(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        loader.clientServiceProvider = { mockClientService }
        loader.refreshResources("AWS::EC2::Instance")

        val paramsCaptor = argumentCaptor<RefreshResourcesParams>()
        verify(mockClientService).refreshResources(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.resources).hasSize(1)
        assertThat(paramsCaptor.firstValue.resources.first().resourceType).isEqualTo("AWS::EC2::Instance")
    }

    @Test
    fun `searchResource adds found resource to cache`() {
        val mockClientService = mock<CfnClientService>()
        val loader = ResourceLoader(projectRule.project)

        val mockResourceSummary = ResourceSummary("AWS::EC2::Instance", listOf("test-instance"))
        val mockResult = SearchResourceResult(found = true, resource = mockResourceSummary)
        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        loader.clientServiceProvider = { mockClientService }

        assertThat(loader.getCachedResources("AWS::EC2::Instance")).isNull()

        val result = loader.searchResource("AWS::EC2::Instance", "test-instance")
        assertThat(result.get()).isTrue()

        val paramsCaptor = argumentCaptor<SearchResourceParams>()
        verify(mockClientService).searchResource(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.resourceType).isEqualTo("AWS::EC2::Instance")
        assertThat(paramsCaptor.firstValue.identifier).isEqualTo("test-instance")

        // Resource should now be in cache
        val cachedResources = loader.getResourceIdentifiers("AWS::EC2::Instance")
        assertThat(cachedResources).contains("test-instance")
        assertThat(loader.isLoaded("AWS::EC2::Instance")).isTrue()
    }

    @Test
    fun `searchResource returns false when resource not found`() {
        val mockClientService = mock<CfnClientService>()
        val loader = ResourceLoader(projectRule.project)

        val mockResult = SearchResourceResult(found = false, resource = null)
        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.completedFuture(mockResult))
        loader.clientServiceProvider = { mockClientService }

        val result = loader.searchResource("AWS::EC2::Instance", "test-instance")

        assertThat(result.get()).isFalse()

        val paramsCaptor = argumentCaptor<SearchResourceParams>()
        verify(mockClientService).searchResource(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.resourceType).isEqualTo("AWS::EC2::Instance")
        assertThat(paramsCaptor.firstValue.identifier).isEqualTo("test-instance")
    }

    @Test
    fun `searchResource handles exception gracefully`() {
        val mockClientService = mock<CfnClientService>()
        val loader = ResourceLoader(projectRule.project)

        whenever(mockClientService.searchResource(any())).thenReturn(CompletableFuture.failedFuture(RuntimeException("Test exception")))
        loader.clientServiceProvider = { mockClientService }

        val result = loader.searchResource("AWS::EC2::Instance", "test-instance")

        assertThat(result.get()).isFalse()
    }

    @Test
    fun `addListener adds listener and notifies on clear`() {
        val loader = ResourceLoader(projectRule.project)
        var notificationReceived = false
        var notifiedResourceType: String? = null

        val listener: ResourcesChangeListener = { resourceType, resources ->
            notificationReceived = true
            notifiedResourceType = resourceType
        }

        loader.addListener(listener)
        loader.clear("AWS::EC2::Instance")

        assertThat(notificationReceived).isTrue()
        assertThat(notifiedResourceType).isEqualTo("AWS::EC2::Instance")
    }

    @Test
    fun `clear with null clears all resource types`() {
        val loader = ResourceLoader(projectRule.project)
        var ec2Cleared = false
        var s3Cleared = false

        loader.addListener { resourceType, resources ->
            when (resourceType) {
                "AWS::EC2::Instance" -> ec2Cleared = resources.isEmpty()
                "AWS::S3::Bucket" -> s3Cleared = resources.isEmpty()
            }
        }

        loader.clear("AWS::EC2::Instance") // Add some state
        loader.clear("AWS::S3::Bucket") // Add some state

        loader.clear(null)

        assertThat(ec2Cleared).isTrue()
        assertThat(s3Cleared).isTrue()
    }

    @Test
    fun `dispose clears listeners`() {
        val loader = ResourceLoader(projectRule.project)
        var notificationReceived = false
        val listener: ResourcesChangeListener = { _, _ -> notificationReceived = true }

        loader.addListener(listener)
        loader.dispose()
        loader.clear("AWS::EC2::Instance")

        // Should not receive notification after dispose
        assertThat(notificationReceived).isFalse()
    }

    @Test
    fun `loadMoreResources does nothing when no nextToken`() {
        val mockClientService = mock<CfnClientService>()
        val loader = ResourceLoader(projectRule.project)
        loader.clientServiceProvider = { mockClientService }

        // First load some resources without nextToken
        val mockResult = RefreshResourcesResult(
            resources = listOf(
                ResourceSummary("AWS::EC2::Instance", listOf("test-instance-1"), null)
            )
        )
        whenever(mockClientService.refreshResources(any())).thenReturn(CompletableFuture.completedFuture(mockResult))

        loader.refreshResources("AWS::EC2::Instance")

        // Reset mock to verify no additional calls
        org.mockito.kotlin.reset(mockClientService)

        loader.loadMoreResources("AWS::EC2::Instance")

        // Should not make any LSP calls since no nextToken
        verify(mockClientService, never()).listResources(any())
    }

    @Test
    fun `loadMoreResources loads additional resources when nextToken exists`() {
        val mockClientService = mock<CfnClientService>()
        val loader = ResourceLoader(projectRule.project)
        loader.clientServiceProvider = { mockClientService }

        // First load with nextToken
        val initialResult = RefreshResourcesResult(
            resources = listOf(
                ResourceSummary("AWS::EC2::Instance", listOf("test-instance-1"), "token123")
            )
        )
        whenever(mockClientService.refreshResources(any())).thenReturn(CompletableFuture.completedFuture(initialResult))

        loader.refreshResources("AWS::EC2::Instance")

        // Mock loadMore response - LSP server returns cumulative results
        val loadMoreResult = ListResourcesResult(
            resources = listOf(
                ResourceSummary("AWS::EC2::Instance", listOf("test-instance-1", "test-instance-2"), null)
            )
        )
        whenever(mockClientService.listResources(any())).thenReturn(CompletableFuture.completedFuture(loadMoreResult))

        loader.loadMoreResources("AWS::EC2::Instance")

        // Verify listResources was called with nextToken
        val paramsCaptor = argumentCaptor<ListResourcesParams>()
        verify(mockClientService).listResources(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.resources?.first()?.nextToken).isEqualTo("token123")

        // Verify resources use server's cumulative results
        val allResources = loader.getResourceIdentifiers("AWS::EC2::Instance")
        assertThat(allResources).containsExactly("test-instance-1", "test-instance-2")
    }
}
