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
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn, "my-test-stack")

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
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

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
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn, "minimal-stack")

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
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn, "date-test-stack")

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
    fun `onStackUpdated triggers stack reload`() {
        val panel = StackOverviewPanel(projectRule.project, mockCoordinator, testStackArn, "my-stack")

        // Create a future we can control
        val futureResult = CompletableFuture<DescribeStackResult?>()
        every { mockCfnClient.describeStack(any()) } returns futureResult

        // Should trigger reload
        panel.onStackUpdated()

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
        assertThat(panel.statusValue.text).isEqualTo("CREATE_COMPLETE")
    }
}
