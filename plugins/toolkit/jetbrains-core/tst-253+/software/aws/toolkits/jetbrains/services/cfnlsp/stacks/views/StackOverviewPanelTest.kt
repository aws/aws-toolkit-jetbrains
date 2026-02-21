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
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackDetail
import java.util.concurrent.CompletableFuture

class StackOverviewPanelTest {

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
        every { mockCoordinator.addListener(any(), any()) } returns mockk()
    }

    @After
    fun tearDown() {
        unmockkObject(CfnClientService)
    }

    @Test
    fun `renderStack updates all field values correctly`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        val testStack = StackDetail(
            stackName = "my-test-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            description = "Test stack description",
            creationTime = "2024-01-15T10:30:45Z",
            lastUpdatedTime = "2024-01-15T11:00:00Z",
            stackStatusReason = "Stack creation completed successfully"
        )

        panel.renderStack(testStack)

        assertThat(panel.stackNameValue.text).isEqualTo("my-test-stack")
        assertThat(panel.statusValue.text).isEqualTo("CREATE_COMPLETE")
        assertThat(panel.stackIdValue.text).isEqualTo(testStackArn)
        assertThat(panel.descriptionValue.text).isEqualTo("Test stack description")
        assertThat(panel.consoleLink.isVisible).isTrue()
    }

    @Test
    fun `renderStack with empty stack ID hides console link`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        val testStack = StackDetail(
            stackName = "test-stack",
            stackId = "",
            stackStatus = "CREATE_COMPLETE",
            description = null,
            creationTime = null,
            lastUpdatedTime = null,
            stackStatusReason = null
        )

        panel.renderStack(testStack)

        assertThat(panel.consoleLink.isVisible).isFalse()
    }

    @Test
    fun `renderStack with null optional fields handles gracefully`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        val testStack = StackDetail(
            stackName = "minimal-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            description = null,
            creationTime = null,
            lastUpdatedTime = null,
            stackStatusReason = null
        )

        panel.renderStack(testStack)

        assertThat(panel.stackNameValue.text).isEqualTo("minimal-stack")
        assertThat(panel.statusValue.text).isEqualTo("CREATE_COMPLETE")
    }

    @Test
    fun `renderStack formats dates correctly`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        val testStack = StackDetail(
            stackName = "date-test-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            description = null,
            creationTime = "2024-01-15T10:30:45Z",
            lastUpdatedTime = "2024-01-15T11:00:00Z",
            stackStatusReason = null
        )

        panel.renderStack(testStack)

        assertThat(panel.createdValue.text).contains("15/1/2024")
        assertThat(panel.lastUpdatedValue.text).contains("15/1/2024")
    }

    @Test
    fun `onStackChanged only responds to matching ARN`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        // Create a future we can control
        val futureResult = CompletableFuture<DescribeStackResult?>()
        every { mockCfnClient.describeStack(any()) } returns futureResult

        val otherStackArn = "arn:aws:cloudformation:us-east-1:123456789012:stack/other-stack/67890"

        // Should not respond to different ARN
        panel.onStackChanged(otherStackArn, "other-stack")
        assertThat(panel.stackNameValue.text).isEqualTo("-") // Should remain unchanged

        // Should respond to matching ARN
        panel.onStackChanged(testStackArn, "my-stack")

        // Complete the future synchronously
        val mockStack = StackDetail(
            stackName = "my-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            description = null,
            creationTime = null,
            lastUpdatedTime = null,
            stackStatusReason = null
        )
        futureResult.complete(DescribeStackResult(mockStack))

        // Process EDT events to execute the invokeLater block from loadStackDetails
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        assertThat(panel.stackNameValue.text).isEqualTo("my-stack")
    }

    @Test
    fun `onStackStatusChanged only responds to matching ARN`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        val otherStackArn = "arn:aws:cloudformation:us-east-1:123456789012:stack/other-stack/67890"

        // Should not respond to different ARN
        panel.onStackStatusChanged(otherStackArn, "CREATE_COMPLETE")
        assertThat(panel.statusValue.text).isEqualTo("Loading...") // Should remain unchanged

        // Should respond to matching ARN
        panel.onStackStatusChanged(testStackArn, "CREATE_COMPLETE")
        assertThat(panel.statusValue.text).isEqualTo("CREATE_COMPLETE")
    }

    @Test
    fun `panel only processes events for its assigned stack ARN`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn)

        // Create a future we can control
        val futureResult = CompletableFuture<DescribeStackResult?>()
        every { mockCfnClient.describeStack(any()) } returns futureResult

        val stack1Arn = "arn:aws:cloudformation:us-east-1:123456789012:stack/stack-1/11111"
        val stack2Arn = "arn:aws:cloudformation:us-east-1:123456789012:stack/stack-2/22222"

        // Events for other stacks should be ignored
        panel.onStackChanged(stack1Arn, "stack-1")
        panel.onStackStatusChanged(stack1Arn, "CREATE_COMPLETE")
        panel.onStackChanged(stack2Arn, "stack-2")
        panel.onStackStatusChanged(stack2Arn, "UPDATE_COMPLETE")

        // Panel should remain in initial state
        assertThat(panel.stackNameValue.text).isEqualTo("-")
        assertThat(panel.statusValue.text).isEqualTo("Loading...")

        // Only events for this panel's ARN should be processed
        panel.onStackChanged(testStackArn, "my-stack")

        // Complete the future synchronously
        val mockStack = StackDetail(
            stackName = "my-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            description = null,
            creationTime = null,
            lastUpdatedTime = null,
            stackStatusReason = null
        )
        futureResult.complete(DescribeStackResult(mockStack))

        // Process EDT events to execute the invokeLater block from loadStackDetails
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        assertThat(panel.stackNameValue.text).isEqualTo("my-stack")

        // Status change should override the status from loadStackDetails
        panel.onStackStatusChanged(testStackArn, "CREATE_IN_PROGRESS")
        assertThat(panel.statusValue.text).isEqualTo("CREATE_IN_PROGRESS")
    }
}
