// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.Output
import com.intellij.execution.OutputListener
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

class PythonAwsConnectionRunConfigurationExtensionTest {

    @Rule
    @JvmField
    val projectRule = PythonCodeInsightTestFixtureRule()

    private val pythonExecutable = System.getenv("PYTHON_PATH")

    @Test
    fun happyPathPythonConnectionInjection() {
        val file = projectRule.fixture.addFileToProject(
            "hello.py", """ 
            import os
            print(os.environ["AWS_REGION"])
        """.trimIndent()
        )

        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", PythonConfigurationType::class.java)
        val runConfiguration = configuration.configuration as PythonRunConfiguration

        runConfiguration.scriptName = file.virtualFile.path
        runConfiguration.sdkHome = pythonExecutable
        val mockRegion = MockRegionProvider.getInstance().defaultRegion().id

        runConfiguration.putCopyableUserData<AwsConnectionRunConfigurationExtensionOptions>(
            AWS_CONNECTION_RUN_CONFIGURATION_KEY,
            AwsConnectionRunConfigurationExtensionOptions {
                region = mockRegion
                credential = MockCredentialsManager.DUMMY_PROVIDER_IDENTIFIER.id
            })

        VfsRootAccess.allowRootAccess(projectRule.fixture.testRootDisposable, "/usr/local/bin/python")

        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        assertNotNull(executor)
        val executionEnvironment = ExecutionEnvironmentBuilder.create(executor, runConfiguration).build()

        val executionFuture = CompletableFuture<Output>()
        runInEdt {
            executionEnvironment.runner.execute(executionEnvironment) {
                it.processHandler?.addProcessListener(object : OutputListener() {
                    override fun processTerminated(event: ProcessEvent) {
                        super.processTerminated(event)
                        executionFuture.complete(this.output)
                    }
                })
            }
        }

        assertThat(executionFuture.get(30, TimeUnit.SECONDS).stdout).isEqualTo(mockRegion)
    }
}
