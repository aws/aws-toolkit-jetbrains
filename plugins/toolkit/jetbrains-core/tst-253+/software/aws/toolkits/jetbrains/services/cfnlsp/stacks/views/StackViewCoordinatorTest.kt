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
        var notifiedStackArn: String? = null
        var notifiedStackName: String? = null

        val listener = object : StackPanelListener {
            override fun onStackChanged(stackArn: String, stackName: String?) {
                notifiedStackArn = stackArn
                notifiedStackName = stackName
            }
            override fun onStackStatusChanged(stackArn: String, status: String?) {}
        }

        coordinator.addListener(testStackArn1, listener)
        coordinator.setStack(testStackArn1, "test-stack-1")

        assertThat(notifiedStackArn).isEqualTo(testStackArn1)
        assertThat(notifiedStackName).isEqualTo("test-stack-1")

        val state = coordinator.getStackState(testStackArn1)
        assertThat(state?.stackName).isEqualTo("test-stack-1")
        assertThat(state?.stackArn).isEqualTo(testStackArn1)
    }

    @Test
    fun `updateStackStatus only notifies listeners for specific stack`() {
        var stack1StatusChanges = 0
        var stack2StatusChanges = 0
        var lastStack1Status: String? = null
        var lastStack2Status: String? = null

        val listener1 = object : StackPanelListener {
            override fun onStackChanged(stackArn: String, stackName: String?) {}
            override fun onStackStatusChanged(stackArn: String, status: String?) {
                if (stackArn == testStackArn1) {
                    stack1StatusChanges++
                    lastStack1Status = status
                }
            }
        }

        val listener2 = object : StackPanelListener {
            override fun onStackChanged(stackArn: String, stackName: String?) {}
            override fun onStackStatusChanged(stackArn: String, status: String?) {
                if (stackArn == testStackArn2) {
                    stack2StatusChanges++
                    lastStack2Status = status
                }
            }
        }

        coordinator.addListener(testStackArn1, listener1)
        coordinator.addListener(testStackArn2, listener2)
        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.setStack(testStackArn2, "stack-2")

        // Update stack 1 status
        coordinator.updateStackStatus(testStackArn1, "CREATE_IN_PROGRESS")
        assertThat(stack1StatusChanges).isEqualTo(1)
        assertThat(stack2StatusChanges).isEqualTo(0)
        assertThat(lastStack1Status).isEqualTo("CREATE_IN_PROGRESS")

        // Update stack 2 status
        coordinator.updateStackStatus(testStackArn2, "UPDATE_COMPLETE")
        assertThat(stack1StatusChanges).isEqualTo(1)
        assertThat(stack2StatusChanges).isEqualTo(1)
        assertThat(lastStack2Status).isEqualTo("UPDATE_COMPLETE")

        // Same status should not notify
        coordinator.updateStackStatus(testStackArn1, "CREATE_IN_PROGRESS")
        assertThat(stack1StatusChanges).isEqualTo(1)
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
        val stack1Events = mutableListOf<String>()
        val stack2Events = mutableListOf<String>()

        val listener1 = object : StackPanelListener {
            override fun onStackChanged(stackArn: String, stackName: String?) {
                if (stackArn == testStackArn1) stack1Events.add("changed:$stackName")
            }
            override fun onStackStatusChanged(stackArn: String, status: String?) {
                if (stackArn == testStackArn1) stack1Events.add("status:$status")
            }
        }

        val listener2 = object : StackPanelListener {
            override fun onStackChanged(stackArn: String, stackName: String?) {
                if (stackArn == testStackArn2) stack2Events.add("changed:$stackName")
            }
            override fun onStackStatusChanged(stackArn: String, status: String?) {
                if (stackArn == testStackArn2) stack2Events.add("status:$status")
            }
        }

        coordinator.addListener(testStackArn1, listener1)
        coordinator.addListener(testStackArn2, listener2)

        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.setStack(testStackArn2, "stack-2")
        coordinator.updateStackStatus(testStackArn1, "COMPLETE")
        coordinator.updateStackStatus(testStackArn2, "FAILED")

        assertThat(stack1Events).containsExactly("changed:stack-1", "status:COMPLETE")
        assertThat(stack2Events).containsExactly("changed:stack-2", "status:FAILED")
    }

    @Test
    fun `new listeners receive immediate notification of current state`() {
        coordinator.setStack(testStackArn1, "existing-stack")
        coordinator.updateStackStatus(testStackArn1, "CREATE_COMPLETE")

        var receivedStackName: String? = null
        var receivedStatus: String? = null

        val listener = object : StackPanelListener {
            override fun onStackChanged(stackArn: String, stackName: String?) {
                if (stackArn == testStackArn1) receivedStackName = stackName
            }
            override fun onStackStatusChanged(stackArn: String, status: String?) {
                if (stackArn == testStackArn1) receivedStatus = status
            }
        }

        // Listener should immediately receive current state
        coordinator.addListener(testStackArn1, listener)

        assertThat(receivedStackName).isEqualTo("existing-stack")
        assertThat(receivedStatus).isEqualTo("CREATE_COMPLETE")
    }

    @Test
    fun `removeStack cleans up state and listeners`() {
        coordinator.setStack(testStackArn1, "stack-1")
        coordinator.addListener(
            testStackArn1,
            object : StackPanelListener {
                override fun onStackChanged(stackArn: String, stackName: String?) {}
                override fun onStackStatusChanged(stackArn: String, status: String?) {}
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
            override fun onStackChanged(stackArn: String, stackName: String?) {
                notificationCount++
            }
            override fun onStackStatusChanged(stackArn: String, status: String?) {}
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
