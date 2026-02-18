// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StackDetailViewCoordinatorTest {

    private lateinit var coordinator: StackViewCoordinator
    private val capturedEvents = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        coordinator = StackViewCoordinator()
        capturedEvents.clear()
    }

    @Test
    fun `setStack updates state and notifies listeners`() {
        var notifiedStackName: String? = null
        var notifiedStackArn: String? = null
        var notifiedChangeSetMode = false

        val listener = object : StackPanelListener {
            override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {
                notifiedStackName = stackName
                notifiedStackArn = stackArn
                notifiedChangeSetMode = isChangeSetMode
            }
            override fun onStackStatusChanged(status: String?) {}
        }

        coordinator.addListener(listener)
        coordinator.setStack("test-stack", "arn:aws:cloudformation:us-east-1:123456789012:stack/test-stack/12345", true)

        assertThat(notifiedStackName).isEqualTo("test-stack")
        assertThat(notifiedStackArn).isEqualTo("arn:aws:cloudformation:us-east-1:123456789012:stack/test-stack/12345")
        assertThat(notifiedChangeSetMode).isTrue()
        assertThat(coordinator.getCurrentStackName()).isEqualTo("test-stack")
    }

    @Test
    fun `updateStackStatus only notifies when status changes`() {
        var statusChangeCount = 0
        var lastStatus: String? = null

        val listener = object : StackPanelListener {
            override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {}
            override fun onStackStatusChanged(status: String?) {
                statusChangeCount++
                lastStatus = status
            }
        }

        coordinator.addListener(listener)

        // First update should notify
        coordinator.updateStackStatus("CREATE_IN_PROGRESS")
        assertThat(statusChangeCount).isEqualTo(1)
        assertThat(lastStatus).isEqualTo("CREATE_IN_PROGRESS")

        // Same status should not notify
        coordinator.updateStackStatus("CREATE_IN_PROGRESS")
        assertThat(statusChangeCount).isEqualTo(1)

        // Different status should notify
        coordinator.updateStackStatus("CREATE_COMPLETE")
        assertThat(statusChangeCount).isEqualTo(2)
        assertThat(lastStatus).isEqualTo("CREATE_COMPLETE")
    }

    @Test
    fun `multiple listeners receive notifications`() {
        val listener1Events = mutableListOf<String>()
        val listener2Events = mutableListOf<String>()

        val listener1 = object : StackPanelListener {
            override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {
                listener1Events.add("stack:$stackName")
            }
            override fun onStackStatusChanged(status: String?) {
                listener1Events.add("status:$status")
            }
        }

        val listener2 = object : StackPanelListener {
            override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {
                listener2Events.add("stack:$stackName")
            }
            override fun onStackStatusChanged(status: String?) {
                listener2Events.add("status:$status")
            }
        }

        coordinator.addListener(listener1)
        coordinator.addListener(listener2)

        coordinator.setStack("test", "arn", false)
        coordinator.updateStackStatus("COMPLETE")

        assertThat(listener1Events).isEqualTo(listOf("stack:test", "status:COMPLETE"))
        assertThat(listener2Events).isEqualTo(listOf("stack:test", "status:COMPLETE"))
    }

    @Test
    fun `listener disposal removes listener`() {
        var notificationCount = 0

        val listener = object : StackPanelListener {
            override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {
                notificationCount++
            }
            override fun onStackStatusChanged(status: String?) {
                notificationCount++
            }
        }

        val disposable = coordinator.addListener(listener)
        coordinator.setStack("test", "arn", false)
        assertThat(notificationCount).isEqualTo(1)

        disposable.dispose()
        coordinator.setStack("test2", "arn2", false)
        assertThat(notificationCount).isEqualTo(1) // Should not increment
    }

    @Test
    fun `getCurrentStackName returns current stack name`() {
        assertThat(coordinator.getCurrentStackName()).isNull()

        coordinator.setStack("my-stack", "my-arn", false)
        assertThat(coordinator.getCurrentStackName()).isEqualTo("my-stack")
    }

    @Test
    fun `dispose clears all listeners`() {
        var notificationCount = 0

        val listener = object : StackPanelListener {
            override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {
                notificationCount++
            }
            override fun onStackStatusChanged(status: String?) {}
        }

        coordinator.addListener(listener)
        coordinator.setStack("test", "arn", false)
        assertThat(notificationCount).isEqualTo(1)

        coordinator.dispose()
        coordinator.setStack("test2", "arn2", false)
        assertThat(notificationCount).isEqualTo(1) // Should not increment
    }
}
