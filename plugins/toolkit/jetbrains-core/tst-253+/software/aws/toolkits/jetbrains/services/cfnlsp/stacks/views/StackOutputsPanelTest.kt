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
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackOutput
import java.util.concurrent.CompletableFuture

class StackOutputsPanelTest {

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
    fun `renderOutputs updates table and count correctly`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "my-test-stack")

        val testOutputs = listOf(
            StackOutput(
                outputKey = "WebsiteURL",
                outputValue = "https://example.com",
                description = "Website URL",
                exportName = "MyStack-WebsiteURL"
            ),
            StackOutput(
                outputKey = "DatabaseEndpoint",
                outputValue = "db.example.com:5432",
                description = "Database connection endpoint",
                exportName = null
            )
        )

        val testStack = StackDetail(
            stackName = "my-test-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            outputs = testOutputs
        )

        panel.renderOutputs(testStack)

        assertThat(panel.outputCountLabel.text).isEqualTo("2 outputs")
        assertThat(panel.consoleLink.isVisible).isTrue()
        assertThat(panel.outputTable.rowCount).isEqualTo(2)
        assertThat(panel.outputTable.getValueAt(0, 0)).isEqualTo("WebsiteURL")
        assertThat(panel.outputTable.getValueAt(0, 1)).isEqualTo("https://example.com")
        assertThat(panel.outputTable.getValueAt(0, 2)).isEqualTo("Website URL")
        assertThat(panel.outputTable.getValueAt(0, 3)).isEqualTo("MyStack-WebsiteURL")
        assertThat(panel.outputTable.getValueAt(1, 3)).isEqualTo("")
    }

    @Test
    fun `renderOutputs with empty outputs shows no outputs found`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "empty-stack")

        val testStack = StackDetail(
            stackName = "empty-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            outputs = emptyList()
        )

        panel.renderOutputs(testStack)

        assertThat(panel.outputCountLabel.text).isEqualTo("0 outputs")
        assertThat(panel.consoleLink.isVisible).isTrue()
        assertThat(panel.outputTable.rowCount).isEqualTo(1)
        assertThat(panel.outputTable.getValueAt(0, 0)).isEqualTo("No outputs found")
        assertThat(panel.outputTable.getValueAt(0, 1)).isEqualTo("")
    }

    @Test
    fun `renderOutputs with single output uses singular form`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "single-output-stack")

        val testStack = StackDetail(
            stackName = "single-output-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            outputs = listOf(
                StackOutput(
                    outputKey = "SingleOutput",
                    outputValue = "single-value",
                    description = "Single output description",
                    exportName = null
                )
            )
        )

        panel.renderOutputs(testStack)

        assertThat(panel.outputCountLabel.text).isEqualTo("1 output")
    }

    @Test
    fun `renderOutputs with empty stack ID hides console link`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "test-stack")

        val testStack = StackDetail(
            stackName = "test-stack",
            stackId = "",
            stackStatus = "CREATE_COMPLETE",
            outputs = listOf(
                StackOutput(
                    outputKey = "TestOutput",
                    outputValue = "test-value",
                    description = null,
                    exportName = null
                )
            )
        )

        panel.renderOutputs(testStack)

        assertThat(panel.consoleLink.isVisible).isFalse()
    }

    @Test
    fun `onStackUpdated triggers outputs reload`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "my-stack")

        // Assert initial state
        assertThat(panel.outputCountLabel.text).isEqualTo("0 outputs")
        assertThat(panel.consoleLink.isVisible).isFalse()
        assertThat(panel.outputTable.rowCount).isEqualTo(1)
        assertThat(panel.outputTable.getValueAt(0, 0)).isEqualTo("No outputs found")

        val futureResult = CompletableFuture<DescribeStackResult?>()
        every { mockCfnClient.describeStack(any()) } returns futureResult

        panel.onStackUpdated()

        val mockStack = StackDetail(
            stackName = "my-stack",
            stackId = testStackArn,
            stackStatus = "CREATE_COMPLETE",
            outputs = listOf(
                StackOutput(
                    outputKey = "TestKey",
                    outputValue = "TestValue",
                    description = "Test Description",
                    exportName = "TestExport"
                )
            )
        )
        futureResult.complete(DescribeStackResult(mockStack))

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        assertThat(panel.outputCountLabel.text).isEqualTo("1 output")
        assertThat(panel.outputTable.getValueAt(0, 0)).isEqualTo("TestKey")
    }

    @Test
    fun `onStackUpdated with error shows error message`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "error-stack")

        val futureResult = CompletableFuture<DescribeStackResult?>()
        every { mockCfnClient.describeStack(any()) } returns futureResult

        panel.onStackUpdated()

        // Complete with error
        futureResult.completeExceptionally(RuntimeException("Test error"))

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        assertThat(panel.outputCountLabel.text).isEqualTo("0 outputs")
        assertThat(panel.consoleLink.isVisible).isFalse()
        assertThat(panel.outputTable.getValueAt(0, 0)).asString().contains("Failed to load outputs:")
        assertThat(panel.outputTable.getValueAt(0, 0)).asString().contains("Test error")
    }

    @Test
    fun `onStackUpdated with null result shows empty state`() {
        val panel = StackOutputsPanel(projectRule.project, mockCoordinator, testStackArn, "null-stack")

        val futureResult = CompletableFuture<DescribeStackResult?>()
        every { mockCfnClient.describeStack(any()) } returns futureResult

        panel.onStackUpdated()

        // Complete with null result
        futureResult.complete(null)

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        assertThat(panel.outputCountLabel.text).isEqualTo("0 outputs")
        assertThat(panel.consoleLink.isVisible).isFalse()
        assertThat(panel.outputTable.getValueAt(0, 0)).isEqualTo("No outputs found")
    }
}
