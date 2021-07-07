// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.Task
import software.aws.toolkits.core.credentials.aCredentialsIdentifier
import software.aws.toolkits.core.region.anAwsRegion
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.MockAwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CompletableFuture

class RunCommandDialogTest {
    @Rule
    @JvmField
    val resourceCache = MockResourceCacheRule()

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val credentialManager = MockCredentialManagerRule()

    private val clusterArn = "arn:aws:ecs:us-east-1:123456789012:cluster/cluster-name"
    private val serviceArn = "arn:aws:ecs:us-east-1:123456789012:service/service-name"
    private val taskArn = "arn:aws:ecs:us-east-1:123456789012:task/task-name"
    private val ecsService = Service.builder().serviceArn(serviceArn).clusterArn(clusterArn).build()

    private val containerDefinition = ContainerDefinition.builder().name("sample-container").build()
    private val container = ContainerDetails(ecsService, containerDefinition)
    lateinit var connectionSettings: ConnectionSettings
    val command = "ls"
    private val task = Task.builder().clusterArn(clusterArn).taskArn(taskArn).build()
    private val taskList = listOf(task.taskArn())
    private val containerName = containerDefinition.name()
    private val verifyCommand = "ecs execute-command --cluster $clusterArn --task $taskArn --command $command --interactive --container $containerName"
    private val dummyRegion = anAwsRegion()

    @Before
    fun setup() {
        val accountSettings = MockAwsConnectionManager.getInstance(projectRule.project)
        val dummyCredential = aCredentialsIdentifier(defaultRegionId = dummyRegion.id)
        val credentials = credentialManager.addCredentials(
            dummyCredential.id,
            AwsBasicCredentials.create("AccessKeyEcsExecDummy", "SecretKeyEcsExecDummy"),
            dummyRegion
        )
        accountSettings.changeCredentialProviderAndWait(credentials)
        connectionSettings = accountSettings.connectionSettings() ?: throw Exception("No credentials found")
    }

    @Test
    fun `Correctly formed string of parameters to execute command is returned`() {
        resourceCache.addEntry(
            projectRule.project, EcsResources.listTasks(clusterArn, serviceArn),
            CompletableFuture.completedFuture(taskList)
        )
        runInEdtAndWait {
            val execCommandParameters = RunCommandDialog(projectRule.project, container, connectionSettings).constructExecCommandParameters(command)
            assertThat(execCommandParameters).isEqualTo(verifyCommand)
        }
    }

    @Test
    fun `Credentials are attached as environment variables when running AWS CLI`() {
        resourceCache.addEntry(
            projectRule.project, EcsResources.listTasks(clusterArn, serviceArn),
            CompletableFuture.completedFuture(taskList)
        )
        val programPath = makeSampleCliExecutable()
        runInEdtAndWait {
            val environmentVariables = mutableListOf<String>()
            val environment =
                ExecutionEnvironmentBuilder
                    .create(
                        projectRule.project,
                        DefaultRunExecutor.getRunExecutorInstance(),
                        RunCommandRunProfile(
                            connectionSettings?.toEnvironmentVariables(),
                            RunCommandDialog(projectRule.project, container, connectionSettings)
                                .constructExecCommandParameters(command),
                            containerName, programPath.toAbsolutePath().toString()
                        )
                    )
                    .build {
                        it.processHandler?.addProcessListener(object : ProcessAdapter() {
                            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                                super.onTextAvailable(event, outputType)
                                environmentVariables.add(event.text.split("\n").first())
                            }

                            override fun processTerminated(event: ProcessEvent) {
                                super.processTerminated(event)
                                assertThat(environmentVariables[1]).isEqualTo("AccessKeyEcsExecDummy")
                                assertThat(environmentVariables[2]).isEqualTo("SecretKeyEcsExecDummy")
                                assertThat(environmentVariables[3]).isEqualTo(dummyRegion)
                                assertThat(environmentVariables[4]).isEqualTo(dummyRegion)
                            }
                        })
                    }
            environment.runner.execute(environment)
        }
    }

    private fun makeSampleCliExecutable(path: String? = null, exitCode: Int = 0): Path {
        val accessKeyId = "\$Env:AWS_ACCESS_KEY_ID"
        val secretAccessKey = "\$Env:AWS_SECRET_ACCESS_KEY"
        val defaultRegion = "\$Env:AWS_DEFAULT_REGION"
        val region = "\$Env:AWS_REGION"
        val execPath = path?.let {
            Paths.get(it)
        } ?: Files.createTempFile(
            "awCli",
            if (SystemInfo.isWindows) ".bat" else ".sh"
        )

        val contents =
            if (SystemInfo.isWindows) {
                """
            echo $accessKeyId
            echo $secretAccessKey
            echo $defaultRegion
            echo $region
            exit $exitCode
                """.trimIndent()
            } else {
                """    
            printenv AWS_ACCESS_KEY_ID
            printenv AWS_SECRET_ACCESS_KEY
            printenv AWS_DEFAULT_REGION
            printenv AWS_REGION
            exit $exitCode
                """.trimIndent()
            }

        Files.write(execPath, contents.toByteArray())

        if (SystemInfo.isUnix) {
            Files.setPosixFilePermissions(
                execPath,
                PosixFilePermissions.fromString("r-xr-xr-x")
            )
        }

        return execPath
    }
}
