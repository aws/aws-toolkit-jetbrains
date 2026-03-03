// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBLabel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackEventsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackEvent
import software.aws.toolkits.jetbrains.utils.notifyInfo
import java.util.concurrent.CompletableFuture

class StackEventsPanelTest {

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
        mockkStatic("software.aws.toolkits.jetbrains.utils.NotificationUtilsKt")
        every { CfnClientService.getInstance(projectRule.project) } returns mockCfnClient
        every { mockCoordinator.addPollingListener(any(), any()) } returns mockk(relaxed = true)
        every { mockCfnClient.clearStackEvents(any()) } returns CompletableFuture.completedFuture(null)
        every { notifyInfo(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(CfnClientService)
        unmockkStatic("software.aws.toolkits.jetbrains.utils.NotificationUtilsKt")
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

    @Test
    fun `loads events on initialization`() {
        val futureResult = CompletableFuture<GetStackEventsResult?>()
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        try {
            verify { mockCfnClient.getStackEvents(any()) }
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `displays events in table correctly`() {
        val events = listOf(
            StackEvent(
                stackId = testStackArn,
                eventId = "event1",
                logicalResourceId = "MyResource",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:00Z",
                operationId = "op1"
            ),
            StackEvent(
                stackId = testStackArn,
                eventId = "event2",
                logicalResourceId = "MyFunction",
                resourceType = "AWS::Lambda::Function",
                resourceStatus = "CREATE_IN_PROGRESS",
                timestamp = "2025-01-01T12:01:00Z",
                operationId = "op2"
            )
        )
        val result = GetStackEventsResult(events, null, false)
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            assertThat(panel.component).isNotNull()
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `pagination buttons work correctly`() {
        val events = (1..100).map {
            StackEvent(
                stackId = testStackArn,
                eventId = "event$it",
                logicalResourceId = "Resource$it",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:${it.toString().padStart(2, '0')}Z",
                operationId = "op$it"
            )
        }
        val result = GetStackEventsResult(events, "nextToken123", false)
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            // Verify button states and text
            assertThat(panel.nextButton.isEnabled).isTrue()
            assertThat(panel.nextButton.text).isEqualTo("Next")
            assertThat(panel.prevButton.isEnabled).isFalse() // Should be disabled on first page
            assertThat(panel.pageLabel.text).isEqualTo("Page 1 of 2") // 100 events = 2 pages
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `shows load more button when more data needed from server`() {
        val events = (1..50).map {
            StackEvent(
                stackId = testStackArn,
                eventId = "event$it",
                logicalResourceId = "Resource$it",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:${it.toString().padStart(2, '0')}Z",
                operationId = "op$it"
            )
        }
        val result = GetStackEventsResult(events, "nextToken123", false)
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            assertThat(panel.nextButton.isEnabled).isTrue()
            assertThat(panel.nextButton.text).isEqualTo("Load More")
            assertThat(panel.prevButton.isEnabled).isFalse()
            assertThat(panel.pageLabel.text).isEqualTo("Page 1 of 1")
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `buttons disabled during loading`() {
        val futureResult = CompletableFuture<GetStackEventsResult?>()
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        try {
            assertThat(panel.nextButton.isEnabled).isFalse()
            assertThat(panel.prevButton.isEnabled).isFalse()
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `page navigation updates button states correctly`() {
        val events = (1..100).map {
            StackEvent(
                stackId = testStackArn,
                eventId = "event$it",
                logicalResourceId = "Resource$it",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:${it.toString().padStart(2, '0')}Z",
                operationId = "op$it"
            )
        }
        val result = GetStackEventsResult(events, null, false)
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            // Initial state: page 1 of 2
            assertThat(panel.pageLabel.text).isEqualTo("Page 1 of 2")
            assertThat(panel.prevButton.isEnabled).isFalse()
            assertThat(panel.nextButton.isEnabled).isTrue()
            assertThat(panel.nextButton.text).isEqualTo("Next")

            // Navigate to page 2
            panel.nextButton.doClick()

            runInEdtAndWait {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            }

            // Page 2 state: last page
            assertThat(panel.pageLabel.text).isEqualTo("Page 2 of 2")
            assertThat(panel.prevButton.isEnabled).isTrue()
            assertThat(panel.nextButton.isEnabled).isFalse()
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `onStackPolled triggers refresh`() {
        val initialEvents = listOf(
            StackEvent(
                stackId = testStackArn,
                eventId = "event1",
                logicalResourceId = "Resource1",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:00Z",
                operationId = "op1"
            )
        )
        val refreshEvents = listOf(
            StackEvent(
                stackId = testStackArn,
                eventId = "event2",
                logicalResourceId = "Resource2",
                resourceType = "AWS::Lambda::Function",
                resourceStatus = "CREATE_IN_PROGRESS",
                timestamp = "2025-01-01T12:01:00Z",
                operationId = "op2"
            )
        )

        val initialResult = CompletableFuture.completedFuture(GetStackEventsResult(initialEvents, null, false))
        val refreshResult = CompletableFuture.completedFuture(GetStackEventsResult(refreshEvents, null, false))

        every { mockCfnClient.getStackEvents(match { it.refresh != true }) } returns initialResult
        every { mockCfnClient.getStackEvents(match { it.refresh == true }) } returns refreshResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        panel.onStackPolled()

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            verify { mockCfnClient.getStackEvents(match { it.refresh == true }) }
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `handles gap detection with full reload`() {
        val initialEvents = listOf(
            StackEvent(
                stackId = testStackArn,
                eventId = "event1",
                logicalResourceId = "Resource1",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:00Z",
                operationId = "op1"
            )
        )
        val gapDetectedResult = GetStackEventsResult(emptyList(), null, true)
        val reloadResult = GetStackEventsResult(initialEvents, null, false)

        val initialResult = CompletableFuture.completedFuture(GetStackEventsResult(initialEvents, null, false))
        val refreshResult = CompletableFuture.completedFuture(gapDetectedResult)
        val fullReloadResult = CompletableFuture.completedFuture(reloadResult)

        every { mockCfnClient.getStackEvents(match { it.refresh != true && it.nextToken == null }) } returnsMany listOf(initialResult, fullReloadResult)
        every { mockCfnClient.getStackEvents(match { it.refresh == true }) } returns refreshResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        panel.onStackPolled()

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            verify(exactly = 3) { mockCfnClient.getStackEvents(any()) }
            // Should show notification to user about gap detection
            verify { notifyInfo("CloudFormation Events", "Event history reloaded due to high activity", projectRule.project) }
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `clears events cache on dispose`() {
        val futureResult = CompletableFuture.completedFuture(GetStackEventsResult(emptyList(), null, false))
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        panel.dispose()

        verify { mockCfnClient.clearStackEvents(any()) }
    }

    @Test
    fun `updates event count label correctly`() {
        val events = (1..75).map {
            StackEvent(
                stackId = testStackArn,
                eventId = "event$it",
                logicalResourceId = "Resource$it",
                resourceType = "AWS::S3::Bucket",
                resourceStatus = "CREATE_COMPLETE",
                timestamp = "2025-01-01T12:00:${it.toString().padStart(2, '0')}Z",
                operationId = "op$it"
            )
        }
        val result = GetStackEventsResult(events, "nextToken123", false)
        val futureResult = CompletableFuture.completedFuture(result)
        every { mockCfnClient.getStackEvents(any()) } returns futureResult

        val panel = StackEventsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        try {
            val eventCountLabelField = panel.javaClass.getDeclaredField("eventCountLabel").apply { isAccessible = true }
            val eventCountLabel = eventCountLabelField.get(panel) as JBLabel

            assertThat(eventCountLabel.text).isEqualTo("75 events loaded")
        } finally {
            panel.dispose()
        }
    }
}
