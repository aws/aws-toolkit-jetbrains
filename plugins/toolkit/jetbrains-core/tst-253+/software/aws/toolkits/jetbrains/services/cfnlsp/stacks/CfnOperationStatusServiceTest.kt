// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase

class CfnOperationStatusServiceTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val service by lazy { CfnOperationStatusService(projectRule.project) }

    @Test
    fun `status text is empty when no operations`() {
        assertThat(service.getStatusText()).isEmpty()
    }

    @Test
    fun `single active validation shows verb and stack name`() {
        service.acquire("my-stack", OperationType.VALIDATION)
        assertThat(service.getStatusText()).isEqualTo("Validating my-stack")
    }

    @Test
    fun `single active deployment shows verb and stack name`() {
        service.acquire("my-stack", OperationType.DEPLOYMENT, "cs-1")
        assertThat(service.getStatusText()).isEqualTo("Deploying my-stack")
    }

    @Test
    fun `single completed validation shows done label`() {
        val handle = service.acquire("my-stack", OperationType.VALIDATION)
        handle.update(StackActionPhase.VALIDATION_COMPLETE)
        assertThat(service.getStatusText()).isEqualTo("Validated my-stack")
    }

    @Test
    fun `single failed validation shows failed label`() {
        val handle = service.acquire("my-stack", OperationType.VALIDATION)
        handle.update(StackActionPhase.VALIDATION_FAILED)
        assertThat(service.getStatusText()).isEqualTo("Validation Failed: my-stack")
    }

    @Test
    fun `single completed deployment shows done label`() {
        val handle = service.acquire("my-stack", OperationType.DEPLOYMENT, "cs-1")
        handle.update(StackActionPhase.DEPLOYMENT_COMPLETE)
        assertThat(service.getStatusText()).isEqualTo("Deployed my-stack")
    }

    @Test
    fun `single failed deployment shows failed label`() {
        val handle = service.acquire("my-stack", OperationType.DEPLOYMENT, "cs-1")
        handle.update(StackActionPhase.DEPLOYMENT_FAILED)
        assertThat(service.getStatusText()).isEqualTo("Deployment Failed: my-stack")
    }

    @Test
    fun `multiple operations shows count`() {
        service.acquire("stack-1", OperationType.VALIDATION)
        service.acquire("stack-2", OperationType.DEPLOYMENT)
        assertThat(service.getStatusText()).isEqualTo("AWS CloudFormation (2)")
    }

    @Test
    fun `released operation excluded from status text`() {
        val handle = service.acquire("my-stack", OperationType.VALIDATION)
        handle.release()
        assertThat(service.getStatusText()).isEmpty()
    }

    @Test
    fun `released operation excluded from active operations`() {
        val handle = service.acquire("my-stack", OperationType.VALIDATION)
        handle.release()
        assertThat(service.getActiveOperations()).isEmpty()
    }

    @Test
    fun `released operation still in all operations`() {
        val handle = service.acquire("my-stack", OperationType.VALIDATION)
        handle.release()
        assertThat(service.getAllOperations()).hasSize(1)
    }

    @Test
    fun `ref counting tracks multiple acquires and releases`() {
        val h1 = service.acquire("stack-1", OperationType.VALIDATION)
        val h2 = service.acquire("stack-2", OperationType.DEPLOYMENT)
        assertThat(service.getActiveOperations()).hasSize(2)

        h1.release()
        assertThat(service.getActiveOperations()).hasSize(1)
        assertThat(service.getStatusText()).isEqualTo("Deploying stack-2")

        h2.release()
        assertThat(service.getActiveOperations()).isEmpty()
    }

    @Test
    fun `update changes phase of correct operation`() {
        val h1 = service.acquire("stack-1", OperationType.VALIDATION)
        val h2 = service.acquire("stack-2", OperationType.DEPLOYMENT)

        h1.update(StackActionPhase.VALIDATION_COMPLETE)

        val ops = service.getActiveOperations()
        val stack1 = ops.first { it.stackName == "stack-1" }
        val stack2 = ops.first { it.stackName == "stack-2" }
        assertThat(stack1.phase).isEqualTo(StackActionPhase.VALIDATION_COMPLETE)
        assertThat(stack2.phase).isEqualTo(StackActionPhase.DEPLOYMENT_IN_PROGRESS)
    }

    @Test
    fun `concurrent acquires produce unique operations`() {
        val handles = (1..10).map { service.acquire("stack-$it", OperationType.VALIDATION) }
        assertThat(service.getActiveOperations()).hasSize(10)
        assertThat(service.getStatusText()).isEqualTo("AWS CloudFormation (10)")

        handles.forEach { it.release() }
        assertThat(service.getActiveOperations()).isEmpty()
    }
}
