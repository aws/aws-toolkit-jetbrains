// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.Tool
import com.intellij.ui.components.JBTextField
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.Task
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import java.util.concurrent.CompletableFuture

class RunCommandDialogTest {
    @Rule
    @JvmField
    val resourceCache = MockResourceCacheRule()

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val clusterArn = "arn:aws:ecs:us-east-1:123456789012:cluster/cluster-name"
    private val serviceArn = "arn:aws:ecs:us-east-1:123456789012:service/service-name"
    private val taskArn = "arn:aws:ecs:us-east-1:123456789012:task/task-name"
    private val ecsService = Service.builder().serviceArn(serviceArn).clusterArn(clusterArn).build()

    private val containerDefinition = ContainerDefinition.builder().name("sample-container").build()
    private val container = ContainerDetails(ecsService, containerDefinition)

    val command = JBTextField("ls")
    private val task = Task.builder().clusterArn(clusterArn).taskArn(taskArn).build()
    private val taskList = listOf(task.taskArn())

    @Test
    fun `Correctly formed string of parameters to execute command is returned`() {
        val verifyCommand = "ecs execute-command --cluster " + clusterArn + " --task " + taskArn + " --command " + command.text + " --interactive"
        resourceCache.addEntry(
            projectRule.project, EcsResources.listTasks(clusterArn, serviceArn),
            CompletableFuture.completedFuture(taskList)
        )
        runInEdtAndWait {
            val execCommandParameters = RunCommandDialog(projectRule.project, container).constructExecCommandParameters(command.text)
            assertThat(execCommandParameters).isEqualTo(verifyCommand)
        }
    }

    @Test
    fun `Run Parameters of Execute Command are set correctly`() {
        val execCommand = Tool()
        val verifyCommand = "ecs execute-command --cluster " + clusterArn + " --task " + taskArn + " --command " + command.text + " --interactive"
        resourceCache.addEntry(
            projectRule.project, EcsResources.listTasks(clusterArn, serviceArn),
            CompletableFuture.completedFuture(taskList)
        )
        runInEdtAndWait {
            RunCommandDialog(projectRule.project, container).updateExecCommandRunSettings(execCommand, command.text)
            assertThat(execCommand.name).isEqualTo("sample-container")
            assertThat(execCommand.parameters).isEqualTo(verifyCommand)
            assertThat(execCommand.isUseConsole).isTrue
            assertThat(execCommand.isShowConsoleOnStdOut).isTrue
            assertThat(execCommand.program).isEqualTo("")
        }
    }
}
