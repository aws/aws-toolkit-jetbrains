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
import java.util.concurrent.CompletableFuture
import javax.swing.JScrollPane
import javax.swing.JTable

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
        every { mockCoordinator.addPollingListener(any(), any()) } returns mockk(relaxed = true)
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
            assertThat(panel.consoleLink.isVisible).isTrue()
            assertThat(panel.resourceCountLabel.text).isEqualTo("2 resources")
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
            assertThat(panel.resourceCountLabel.text).isEqualTo("100 resources loaded")
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
            assertThat(panel.resourceCountLabel.text).isEqualTo("50 resources loaded")
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
    fun `onStackPolled resets to page 1 and reloads data`() {
        val initialResources = (1..100).map {
            StackResourceSummary("LogicalId$it", "PhysicalId$it", "AWS::S3::Bucket", "CREATE_COMPLETE", null)
        }
        val refreshedResources = (1..75).map {
            StackResourceSummary("NewId$it", "NewPhysical$it", "AWS::Lambda::Function", "UPDATE_COMPLETE", null)
        }

        val initialResult = CompletableFuture.completedFuture(ListStackResourcesResult(initialResources, null))
        val refreshResult = CompletableFuture.completedFuture(ListStackResourcesResult(refreshedResources, null))

        every { mockCfnClient.getStackResources(any()) } returnsMany listOf(initialResult, refreshResult)

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

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

            // Simulate coordinator calling onStackPolled
            panel.onStackPolled()

            runInEdtAndWait {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            }

            // Verify polling refresh made new LSP call and reset to page 1
            verify(atLeast = 2) { mockCfnClient.getStackResources(any()) }
            assertThat(panel.pageLabel.text).isEqualTo("Page 1")
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

    @Test
    fun `handles API error correctly`() {
        val futureResult = CompletableFuture<ListStackResourcesResult?>()
        every { mockCfnClient.getStackResources(any()) } returns futureResult

        val panel = StackResourcesPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        futureResult.completeExceptionally(RuntimeException("API error"))

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            assertThat(panel.consoleLink.isVisible).isFalse()
            assertThat(panel.prevButton.isEnabled).isFalse()
            assertThat(panel.nextButton.isEnabled).isFalse()
            assertThat(panel.pageLabel.text).isEqualTo("Page 1")
            assertThat(panel.resourceCountLabel.text).isEqualTo("0 resources")
            // Verify error message is displayed in table
            val tableModel = panel.component.components
                .filterIsInstance<JScrollPane>()
                .first().viewport.view as JTable
            assertThat(tableModel.getValueAt(0, 0)).asString().contains("Failed to load resources:")
            assertThat(tableModel.getValueAt(0, 0)).asString().contains("API error")
        } finally {
            panel.dispose()
        }
    }
}
