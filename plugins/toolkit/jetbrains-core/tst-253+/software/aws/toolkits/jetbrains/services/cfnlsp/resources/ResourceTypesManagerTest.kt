// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceTypesResult
import java.util.concurrent.CompletableFuture

class ResourceTypesManagerTest {

    @get:Rule
    val projectRule = ProjectRule()

    @Test
    fun `initially has no selected resource types`() {
        val manager = ResourceTypesManager(projectRule.project)

        assertThat(manager.getSelectedResourceTypes()).isEmpty()
        assertThat(manager.areTypesLoaded()).isFalse()
        assertThat(manager.getAvailableResourceTypes()).isEmpty()
    }

    @Test
    fun `can add resource types`() {
        val manager = ResourceTypesManager(projectRule.project)

        manager.addResourceType("AWS::EC2::Instance")
        assertThat(manager.getSelectedResourceTypes()).containsExactly("AWS::EC2::Instance")

        manager.addResourceType("AWS::S3::Bucket")
        assertThat(manager.getSelectedResourceTypes()).containsExactlyInAnyOrder(
            "AWS::EC2::Instance",
            "AWS::S3::Bucket"
        )
    }

    @Test
    fun `loads available types from LSP server`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)

        val mockResult = ResourceTypesResult(listOf("AWS::EC2::Instance", "AWS::S3::Bucket"))
        whenever(mockClientService.listResourceTypes()).thenReturn(CompletableFuture.completedFuture(mockResult))

        manager.clientServiceProvider = { mockClientService }

        manager.loadAvailableTypes()
        testScheduler.advanceUntilIdle()

        verify(mockClientService).listResourceTypes()
        assertThat(manager.getAvailableResourceTypes()).containsExactlyInAnyOrder("AWS::EC2::Instance", "AWS::S3::Bucket")
        assertThat(manager.areTypesLoaded()).isTrue()
    }

    @Test
    fun `removeResourceType sends request to LSP server`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)

        whenever(mockClientService.removeResourceType(any())).thenReturn(CompletableFuture.completedFuture(null))

        manager.clientServiceProvider = { mockClientService }

        manager.addResourceType("AWS::EC2::Instance")
        assertThat(manager.getSelectedResourceTypes()).containsExactly("AWS::EC2::Instance")

        manager.removeResourceType("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        verify(mockClientService).removeResourceType("AWS::EC2::Instance")
        assertThat(manager.getSelectedResourceTypes()).isEmpty()
    }

    @Test
    fun `adding duplicate resource type is ignored`() {
        val manager = ResourceTypesManager(projectRule.project)

        manager.addResourceType("AWS::EC2::Instance")
        manager.addResourceType("AWS::EC2::Instance")

        assertThat(manager.getSelectedResourceTypes()).containsExactly("AWS::EC2::Instance")
    }

    @Test
    fun `removeResourceType removes from state immediately even when LSP server fails`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)

        whenever(mockClientService.removeResourceType(any())).thenReturn(CompletableFuture.failedFuture(RuntimeException("Test exception")))

        manager.clientServiceProvider = { mockClientService }

        manager.addResourceType("AWS::EC2::Instance")
        assertThat(manager.getSelectedResourceTypes()).containsExactly("AWS::EC2::Instance")

        manager.removeResourceType("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        verify(mockClientService).removeResourceType("AWS::EC2::Instance")
        // Should remove from state immediately for responsive UI, even if LSP call fails
        assertThat(manager.getSelectedResourceTypes()).isEmpty()
    }

    @Test
    fun `removeResourceType does nothing for non-existent type`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)
        manager.clientServiceProvider = { mockClientService }

        manager.removeResourceType("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        verify(mockClientService, never()).removeResourceType(any())
        assertThat(manager.getSelectedResourceTypes()).isEmpty()
    }

    @Test
    fun `loadAvailableTypes handles null result gracefully`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)

        whenever(mockClientService.listResourceTypes()).thenReturn(CompletableFuture.completedFuture(null))

        manager.clientServiceProvider = { mockClientService }

        manager.loadAvailableTypes()
        testScheduler.advanceUntilIdle()

        verify(mockClientService).listResourceTypes()
        assertThat(manager.getAvailableResourceTypes()).isEmpty()
        assertThat(manager.areTypesLoaded()).isFalse()
    }

    @Test
    fun `listeners are notified when resource types change`() {
        val manager = ResourceTypesManager(projectRule.project)
        var notificationCount = 0
        val listener: ResourceTypesChangeListener = { notificationCount++ }

        manager.addListener(listener)

        manager.addResourceType("AWS::EC2::Instance")
        assertThat(notificationCount).isEqualTo(1)

        manager.addResourceType("AWS::S3::Bucket")
        assertThat(notificationCount).isEqualTo(2)

        // Adding duplicate should not notify
        manager.addResourceType("AWS::EC2::Instance")
        assertThat(notificationCount).isEqualTo(2)
    }

    @Test
    fun `listeners are notified when resource types are removed successfully`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)

        whenever(mockClientService.removeResourceType(any())).thenReturn(CompletableFuture.completedFuture(null))

        manager.clientServiceProvider = { mockClientService }

        var notificationCount = 0
        val listener: ResourceTypesChangeListener = { notificationCount++ }
        manager.addListener(listener)

        manager.addResourceType("AWS::EC2::Instance")
        assertThat(notificationCount).isEqualTo(1)

        manager.removeResourceType("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        assertThat(notificationCount).isEqualTo(2)
    }

    @Test
    fun `listeners are notified immediately when resource type removal is requested`() = runTest {
        val mockClientService = mock<CfnClientService>()
        val manager = ResourceTypesManager(projectRule.project)

        whenever(mockClientService.removeResourceType(any())).thenReturn(CompletableFuture.failedFuture(RuntimeException("Test exception")))

        manager.clientServiceProvider = { mockClientService }

        var notificationCount = 0
        val listener: ResourceTypesChangeListener = { notificationCount++ }
        manager.addListener(listener)

        manager.addResourceType("AWS::EC2::Instance")
        assertThat(notificationCount).isEqualTo(1)

        manager.removeResourceType("AWS::EC2::Instance")
        testScheduler.advanceUntilIdle()

        // Should notify immediately when removal is requested (responsive UI)
        assertThat(notificationCount).isEqualTo(2)
    }

    @Test
    fun `state persistence works correctly`() {
        val manager = ResourceTypesManager(projectRule.project)

        manager.addResourceType("AWS::EC2::Instance")
        manager.addResourceType("AWS::S3::Bucket")

        val state = manager.state
        assertThat(state.selectedTypes).containsExactlyInAnyOrder("AWS::EC2::Instance", "AWS::S3::Bucket")

        // Simulate loading state
        val newManager = ResourceTypesManager(projectRule.project)
        newManager.loadState(state)

        assertThat(newManager.getSelectedResourceTypes()).containsExactlyInAnyOrder("AWS::EC2::Instance", "AWS::S3::Bucket")
    }
}
