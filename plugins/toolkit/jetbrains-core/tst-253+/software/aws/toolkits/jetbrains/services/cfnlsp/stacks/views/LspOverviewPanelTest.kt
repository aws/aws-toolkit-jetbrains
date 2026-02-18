// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.LspStack

class LspOverviewPanelTest {

    @get:Rule
    val projectRule = ProjectRule()

    @Test
    fun `renderStack updates all field values correctly`() {
        val mockCoordinator = mock<LspStackViewCoordinator>()
        val panel = LspOverviewPanel(projectRule.project, mockCoordinator)

        val testStack = LspStack(
            stackName = "my-test-stack",
            stackId = "arn:aws:cloudformation:us-east-1:123456789012:stack/my-test-stack/12345",
            stackStatus = "CREATE_COMPLETE",
            description = "Test stack description",
            creationTime = "2024-01-15T10:30:45Z",
            lastUpdatedTime = "2024-01-15T11:00:00Z",
            stackStatusReason = "Stack creation completed successfully"
        )

        panel.renderStack(testStack)

        assertThat(panel.stackNameValue.text).isEqualTo("my-test-stack")
        assertThat(panel.statusValue.text).isEqualTo("CREATE_COMPLETE")
        assertThat(panel.stackIdValue.text).isEqualTo(testStack.stackId)
        assertThat(panel.descriptionValue.text).isEqualTo("Test stack description")
        assertThat(panel.consoleLink.isVisible).isTrue()
    }

    @Test
    fun `renderStack with empty stack ID hides console link`() {
        val mockCoordinator = mock<LspStackViewCoordinator>()
        val panel = LspOverviewPanel(projectRule.project, mockCoordinator)

        val testStack = LspStack(
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
        val mockCoordinator = mock<LspStackViewCoordinator>()
        val panel = LspOverviewPanel(projectRule.project, mockCoordinator)

        val testStack = LspStack(
            stackName = "minimal-stack",
            stackId = "arn:aws:cloudformation:us-east-1:123456789012:stack/minimal-stack/12345",
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
        val mockCoordinator = mock<LspStackViewCoordinator>()
        val panel = LspOverviewPanel(projectRule.project, mockCoordinator)

        val testStack = LspStack(
            stackName = "date-test-stack",
            stackId = "arn:aws:cloudformation:us-east-1:123456789012:stack/date-test-stack/12345",
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
    fun `onStackChanged with null shows empty state`() {
        val mockCoordinator = mock<LspStackViewCoordinator>()
        val panel = LspOverviewPanel(projectRule.project, mockCoordinator)

        panel.onStackChanged(null, null, false)

        assertThat(panel.stackNameValue.text).isEqualTo("Select a stack to view details")
        assertThat(panel.consoleLink.isVisible).isFalse()
    }

    @Test
    fun `onStackChanged with change set mode shows empty state`() {
        val mockCoordinator = mock<LspStackViewCoordinator>()
        val panel = LspOverviewPanel(projectRule.project, mockCoordinator)

        panel.onStackChanged("test-stack", "test-arn", true)

        assertThat(panel.stackNameValue.text).isEqualTo("Select a stack to view details")
    }
}
