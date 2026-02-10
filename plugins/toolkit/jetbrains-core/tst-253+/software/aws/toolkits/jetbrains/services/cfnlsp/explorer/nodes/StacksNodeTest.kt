// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager

class StacksNodeTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockStacksManager: StacksManager
    private lateinit var mockChangeSetsManager: ChangeSetsManager
    private lateinit var stacksNode: StacksNode

    @Before
    fun setUp() {
        mockStacksManager = mock()
        mockChangeSetsManager = mock()
        stacksNode = StacksNode(projectRule.project, mockStacksManager, mockChangeSetsManager)
    }

    @Test
    fun `getChildren returns empty and triggers reload when not loaded`() {
        whenever(mockStacksManager.isLoaded()).thenReturn(false)

        val children = stacksNode.children

        assertThat(children).isEmpty()
        org.mockito.kotlin.verify(mockStacksManager).reload()
    }

    @Test
    fun `getChildren returns NoStacksNode when loaded but empty`() {
        whenever(mockStacksManager.isLoaded()).thenReturn(true)
        whenever(mockStacksManager.get()).thenReturn(emptyList())

        val children = stacksNode.children

        assertThat(children).hasSize(1)
        assertThat(children.first()).isInstanceOf(NoStacksNode::class.java)
    }

    @Test
    fun `getChildren returns stack nodes when loaded with stacks`() {
        whenever(mockStacksManager.isLoaded()).thenReturn(true)
        whenever(mockStacksManager.get()).thenReturn(
            listOf(
                StackSummary(StackName = "stack-1", StackStatus = "CREATE_COMPLETE"),
                StackSummary(StackName = "stack-2", StackStatus = "UPDATE_COMPLETE")
            )
        )
        whenever(mockStacksManager.hasMore()).thenReturn(false)

        val children = stacksNode.children

        assertThat(children).hasSize(2)
        assertThat(children).allMatch { it is StackNode }
    }

    @Test
    fun `getChildren includes LoadMoreStacksNode when hasMore is true`() {
        whenever(mockStacksManager.isLoaded()).thenReturn(true)
        whenever(mockStacksManager.get()).thenReturn(
            listOf(
                StackSummary(StackName = "stack-1", StackStatus = "CREATE_COMPLETE")
            )
        )
        whenever(mockStacksManager.hasMore()).thenReturn(true)

        val children = stacksNode.children

        assertThat(children).hasSize(2)
        assertThat(children.last()).isInstanceOf(LoadMoreStacksNode::class.java)
    }

    @Test
    fun `isAlwaysShowPlus returns true`() {
        assertThat(stacksNode.isAlwaysShowPlus).isTrue()
    }
}

class StackNodeTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockChangeSetsManager: ChangeSetsManager

    @Before
    fun setUp() {
        mockChangeSetsManager = mock()
    }

    @Test
    fun `getStackIcon returns success icon for CREATE_COMPLETE`() {
        val icon = getIconForStatus("CREATE_COMPLETE")
        assertThat(icon).isEqualTo(AllIcons.General.InspectionsOK)
    }

    @Test
    fun `getStackIcon returns success icon for UPDATE_COMPLETE`() {
        val icon = getIconForStatus("UPDATE_COMPLETE")
        assertThat(icon).isEqualTo(AllIcons.General.InspectionsOK)
    }

    @Test
    fun `getStackIcon returns error icon for CREATE_FAILED`() {
        val icon = getIconForStatus("CREATE_FAILED")
        assertThat(icon).isEqualTo(AllIcons.General.Error)
    }

    @Test
    fun `getStackIcon returns error icon for ROLLBACK_COMPLETE`() {
        val icon = getIconForStatus("ROLLBACK_COMPLETE")
        assertThat(icon).isEqualTo(AllIcons.General.Error)
    }

    @Test
    fun `getStackIcon returns error icon for UPDATE_ROLLBACK_COMPLETE`() {
        val icon = getIconForStatus("UPDATE_ROLLBACK_COMPLETE")
        assertThat(icon).isEqualTo(AllIcons.General.Error)
    }

    @Test
    fun `getStackIcon returns progress icon for CREATE_IN_PROGRESS`() {
        val icon = getIconForStatus("CREATE_IN_PROGRESS")
        assertThat(icon).isEqualTo(AllIcons.Process.Step_1)
    }

    @Test
    fun `getStackIcon returns progress icon for UPDATE_IN_PROGRESS`() {
        val icon = getIconForStatus("UPDATE_IN_PROGRESS")
        assertThat(icon).isEqualTo(AllIcons.Process.Step_1)
    }

    @Test
    fun `getStackIcon returns folder icon for null status`() {
        val icon = getIconForStatus(null)
        assertThat(icon).isEqualTo(AllIcons.Nodes.Folder)
    }

    @Test
    fun `getStackIcon returns folder icon for unknown status`() {
        val icon = getIconForStatus("UNKNOWN_STATUS")
        assertThat(icon).isEqualTo(AllIcons.Nodes.Folder)
    }

    private fun getIconForStatus(status: String?) = when {
        status == null -> AllIcons.Nodes.Folder
        status.contains("COMPLETE") && !status.contains("ROLLBACK") -> AllIcons.General.InspectionsOK
        status.contains("FAILED") || status.contains("ROLLBACK") -> AllIcons.General.Error
        status.contains("PROGRESS") -> AllIcons.Process.Step_1
        else -> AllIcons.Nodes.Folder
    }
}
