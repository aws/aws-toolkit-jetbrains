// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StackViewCoordinatorTest {

    private lateinit var coordinator: StackViewCoordinator
    private val testStackArn1 = "arn:aws:cloudformation:us-east-1:123456789012:stack/test-stack-1/12345"
    private val testStackArn2 = "arn:aws:cloudformation:us-east-1:123456789012:stack/test-stack-2/67890"

    @BeforeEach
    fun setUp() {
        coordinator = StackViewCoordinator()
    }

    @Test
    fun `setStack updates state and notifies listeners for specific stack`() {
        var notificationCount = 0

        val listener = object : StackPanelListener {
            override fun onStackUpdated() {
                notificationCount++
            }
        }

        coordinator.addListener(testStackArn1, listener)
        coordinator.setStack(testStackArn1, "test-stack-1")

        assertThat(notificationCount).isEqualTo(1)

        val state = coordinator.getStackState(testStackArn1)
        assertThat(state?.stackName).isEqualTo("test-stack-1")
        assertThat(state?.stackArn).isEqualTo(testStackArn1)
    }

    @Test
    fun `updateStackStatus only notifies listeners for specific stack`() {
        var stack1Updates = 0
        var stack2Updates = 0

        val listener1 = object : StackPanelListener {
            override fun onStackUpdated() {
                stack1Updates++
            }
        }

        val listener2 = object : StackPanelListener {
            override fun onStackUpdated() {
                stack2Updates++
            }
        }

        coordinator.addListener(testStackArn1, listener1)
        coordinator.addListener(testStackArn2, listener2)
        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.setStack(testStackArn2, "stack-2")

        // Reset counters after initial setStack calls
        stack1Updates = 0
        stack2Updates = 0

        // Update stack 1 status
        coordinator.updateStackStatus(testStackArn1, "CREATE_IN_PROGRESS")
        assertThat(stack1Updates).isEqualTo(1)
        assertThat(stack2Updates).isEqualTo(0)

        // Update stack 2 status
        coordinator.updateStackStatus(testStackArn2, "UPDATE_COMPLETE")
        assertThat(stack1Updates).isEqualTo(1)
        assertThat(stack2Updates).isEqualTo(1)

        // Same status should not notify
        coordinator.updateStackStatus(testStackArn1, "CREATE_IN_PROGRESS")
        assertThat(stack1Updates).isEqualTo(1)
    }

    @Test
    fun `multiple stacks can be managed independently`() {
        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.setStack(testStackArn2, "stack-2")

        val state1 = coordinator.getStackState(testStackArn1)
        val state2 = coordinator.getStackState(testStackArn2)

        assertThat(state1?.stackName).isEqualTo("stack-1")
        assertThat(state2?.stackName).isEqualTo("stack-2")

        coordinator.updateStackStatus(testStackArn1, "CREATE_COMPLETE")
        coordinator.updateStackStatus(testStackArn2, "UPDATE_IN_PROGRESS")

        val updatedState1 = coordinator.getStackState(testStackArn1)
        val updatedState2 = coordinator.getStackState(testStackArn2)

        assertThat(updatedState1?.status).isEqualTo("CREATE_COMPLETE")
        assertThat(updatedState2?.status).isEqualTo("UPDATE_IN_PROGRESS")
    }

    @Test
    fun `listeners only receive notifications for their registered stack`() {
        val stack1Updates = mutableListOf<String>()
        val stack2Updates = mutableListOf<String>()

        val listener1 = object : StackPanelListener {
            override fun onStackUpdated() {
                stack1Updates.add("updated")
            }
        }

        val listener2 = object : StackPanelListener {
            override fun onStackUpdated() {
                stack2Updates.add("updated")
            }
        }

        coordinator.addListener(testStackArn1, listener1)
        coordinator.addListener(testStackArn2, listener2)

        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.setStack(testStackArn2, "stack-2")
        coordinator.updateStackStatus(testStackArn1, "COMPLETE")
        coordinator.updateStackStatus(testStackArn2, "FAILED")

        // Each listener should receive 2 notifications (setStack + updateStackStatus)
        assertThat(stack1Updates).hasSize(2)
        assertThat(stack2Updates).hasSize(2)
    }

    @Test
    fun `new listeners receive immediate notification of current state`() {
        coordinator.setStack(testStackArn1, "existing-stack")
        coordinator.updateStackStatus(testStackArn1, "CREATE_COMPLETE")

        var notificationCount = 0

        val listener = object : StackPanelListener {
            override fun onStackUpdated() {
                notificationCount++
            }
        }

        // Listener should immediately receive current state
        coordinator.addListener(testStackArn1, listener)

        assertThat(notificationCount).isEqualTo(1)
    }

    @Test
    fun `removeStack cleans up state and listeners`() {
        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.addListener(
            testStackArn1,
            object : StackPanelListener {
                override fun onStackUpdated() {}
            }
        )

        assertThat(coordinator.getStackState(testStackArn1)).isNotNull()

        coordinator.removeStack(testStackArn1)

        assertThat(coordinator.getStackState(testStackArn1)).isNull()
    }

    @Test
    fun `listener disposal removes listener for specific stack`() {
        var notificationCount = 0

        val listener = object : StackPanelListener {
            override fun onStackUpdated() {
                notificationCount++
            }
        }

        val disposable = coordinator.addListener(testStackArn1, listener)
        coordinator.setStack(testStackArn1, "test")
        assertThat(notificationCount).isEqualTo(1)

        disposable.dispose()
        coordinator.setStack(testStackArn1, "test-updated")
        assertThat(notificationCount).isEqualTo(1) // Should not increment
    }

    @Test
    fun `dispose clears all stacks and listeners`() {
        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.setStack(testStackArn2, "stack-2")

        assertThat(coordinator.getStackState(testStackArn1)).isNotNull()
        assertThat(coordinator.getStackState(testStackArn2)).isNotNull()

        coordinator.dispose()

        assertThat(coordinator.getStackState(testStackArn1)).isNull()
        assertThat(coordinator.getStackState(testStackArn2)).isNull()
    }
}
