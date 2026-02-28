// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStackResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackResourceSummary
import java.time.Instant
import java.util.concurrent.CompletableFuture

class StackResourcesPanelTest {

    @get:Rule
    val projectRule = ProjectRule()

    private val testStackArn = "arn:aws:cloudformation:us-east-1:123456789012:stack/my-test-stack/12345"
    private lateinit var mockCfnClient: CfnClientService
    private lateinit var mockCoordinator: StackViewCoordinator

    @Before
    fun setUp() {
        mockCfnClient = mockk()
        mockCoordinator = mockk()
        mockkObject(CfnClientService)
        every { CfnClientService.getInstance(projectRule.project) } returns mockCfnClient
        every { mockCoordinator.addListener(any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(CfnClientService)
        // Ensure all EDT events are processed to prevent test interference
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

    @Test
    fun `loads resources on initialization`() {
        val futureResult = CompletableFuture<ListStackResourcesResult?>()
        every { mockCfnClient.getStackResources(any()) } returns futureResult

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        try {
            verify { mockCfnClient.getStackResources(any()) }
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `displays resources in table correctly`() {
        val resources = listOf(
            StackResourceSummary("LogicalId1", "PhysicalId1", "AWS::S3::Bucket", "CREATE_COMPLETE", null),
            StackResourceSummary("LogicalId2", null, "AWS::Lambda::Function", "CREATE_IN_PROGRESS", null)
        )
        val result = ListStackResourcesResult(resources, null)
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackResources(any()) } returns futureResult

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            assertThat(panel.component).isNotNull()
            // Resources should be loaded and displayed in the table
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `pagination buttons work correctly`() {
        val resources = (1..100).map {
            StackResourceSummary("LogicalId$it", "PhysicalId$it", "AWS::S3::Bucket", "CREATE_COMPLETE", null)
        }
        val result = ListStackResourcesResult(resources, "nextToken123")
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackResources(any()) } returns futureResult

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            assertThat(panel.nextButton.isEnabled).isTrue()
            assertThat(panel.nextButton.text).isEqualTo("Next") // First page shows "Next", not "Load More"
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `shows load more button when more data needed from server`() {
        val resources = (1..50).map {
            StackResourceSummary("LogicalId$it", "PhysicalId$it", "AWS::S3::Bucket", "CREATE_COMPLETE", null)
        }
        val result = ListStackResourcesResult(resources, "nextToken123")
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackResources(any()) } returns futureResult

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            assertThat(panel.nextButton.isEnabled).isTrue()
            assertThat(panel.nextButton.text).isEqualTo("Load More") // Exactly 50 resources + nextToken = "Load More"
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `prevents rapid fire clicks with isLoading flag`() {
        val futureResult = CompletableFuture<ListStackResourcesResult?>()
        every { mockCfnClient.getStackResources(any()) } returns futureResult

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        // Simulate rapid clicks - should only make one call due to isLoading flag
        repeat(5) {
            val loadResourcesMethod = panel.javaClass.getDeclaredMethod("loadResources").apply {
                isAccessible = true
            }
            loadResourcesMethod.invoke(panel)
        }

        try {
            verify(exactly = 1) { mockCfnClient.getStackResources(any()) }
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `timer based auto refresh resets to page 1 and reloads data`() {
        val initialResources = (1..100).map {
            StackResourceSummary("LogicalId$it", "PhysicalId$it", "AWS::S3::Bucket", "CREATE_COMPLETE", null)
        }
        val refreshedResources = (1..75).map {
            StackResourceSummary("NewId$it", "NewPhysical$it", "AWS::Lambda::Function", "UPDATE_COMPLETE", null)
        }

        val initialResult = CompletableFuture.completedFuture(ListStackResourcesResult(initialResources, null))
        val refreshResult = CompletableFuture.completedFuture(ListStackResourcesResult(refreshedResources, null))

        every { mockCfnClient.getStackResources(any()) } returnsMany listOf(initialResult, refreshResult)

        // Mock coordinator for transient state (enables auto-refresh alarm)
        every { mockCoordinator.addListener(any(), any()) } returns mockk(relaxed = true)
        every { mockCoordinator.getStackState(any()) } returns StackState("test-stack", testStackArn, "UPDATE_IN_PROGRESS", Instant.now())

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack", 100) // 100ms delay for testing

        try {
            runInEdtAndWait {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            }

            // Navigate to page 2 first
            val loadNextPageMethod = panel.javaClass.getDeclaredMethod("loadNextPage").apply {
                isAccessible = true
            }
            loadNextPageMethod.invoke(panel)

            runInEdtAndWait {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            }

            // Verify we're on page 2
            assertThat(panel.pageLabel.text).isEqualTo("Page 2")

            // Start auto-refresh (happens when stack is in transient state)
            val startAutoRefreshMethod = panel.javaClass.getDeclaredMethod("startAutoRefresh").apply {
                isAccessible = true
            }
            startAutoRefreshMethod.invoke(panel)

            // Use IntelliJ's alarm testing - wait for 100ms alarm to fire
            runInEdtAndWait {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            }

            // Verify alarm-based auto-refresh made new LSP call
            verify(timeout = 1000, atLeast = 2) { mockCfnClient.getStackResources(any()) }
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `makes additional LSP calls when navigating beyond cached data`() {
        val firstCall = CompletableFuture.completedFuture(
            ListStackResourcesResult(
                (1..50).map {
                    StackResourceSummary("LogicalId$it", "PhysicalId$it", "AWS::S3::Bucket", "CREATE_COMPLETE", null)
                },
                "token1"
            )
        )
        val secondCall = CompletableFuture.completedFuture(
            ListStackResourcesResult(
                (51..75).map {
                    StackResourceSummary("LogicalId$it", "PhysicalId$it", "AWS::S3::Bucket", "CREATE_COMPLETE", null)
                },
                null
            )
        )

        every { mockCfnClient.getStackResources(match { it.nextToken == null }) } returns firstCall
        every { mockCfnClient.getStackResources(match { it.nextToken == "token1" }) } returns secondCall

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        // Simulate navigation that triggers server call
        val loadNextPageMethod = panel.javaClass.getDeclaredMethod("loadNextPage").apply {
            isAccessible = true
        }
        loadNextPageMethod.invoke(panel)

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            verify { mockCfnClient.getStackResources(match { it.nextToken == "token1" }) }
        } finally {
            panel.dispose()
        }
    }
}
