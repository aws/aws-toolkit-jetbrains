// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.application.options.RegistryManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.services.lambda.execution.local.createHandlerBasedRunConfiguration
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommonTestUtils
import software.aws.toolkits.jetbrains.utils.executeRunConfigurationAndWait
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.setSamExecutable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class SamDebuggerTimeoutTest {
    @Rule
    @JvmField
    val projectRule = PythonCodeInsightTestFixtureRule()

    private val mockId = "MockCredsId"
    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        val fixture = projectRule.fixture
        fixture.addFileToProject(
            "src/hello_world/__init__.py",
            ""
        )

        val psiClass = fixture.addFileToProject(
            "src/hello_world/app.py",
            """
            import os

            def lambda_handler(event, context):
                print(os.environ)
                return "Hello world"

            def env_print(event, context):
                return dict(**os.environ)
            """.trimIndent()
        )

        runInEdtAndWait {
            fixture.openFileInEditor(psiClass.containingFile.virtualFile)
        }

        MockCredentialsManager.getInstance().addCredentials(mockId, mockCreds)
    }

    @Test
    fun `does not timeout if SAM has output`() {
        projectRule.fixture.addFileToProject("requirements.txt", "")

        val runConfiguration = createHandlerBasedRunConfiguration(
            project = projectRule.project,
            runtime = Runtime.PYTHON3_8,
            handler = "src/hello_world.app.lambda_handler",
            input = "\"Hello World\"",
            credentialsProviderId = mockId
        )
        assertThat(runConfiguration).isNotNull

        val samPath = makeASam()
        setSamExecutable(samPath)

        val timeoutRegistryKey = RegistryManager.getInstance().get("aws.debuggerAttach.timeout")
        try {
            timeoutRegistryKey.setValue(Duration.ofSeconds(5).toMillis().toInt())

            val executeLambda = executeRunConfigurationAndWait(runConfiguration, DefaultDebugExecutor.EXECUTOR_ID)
            assertThat(executeLambda.exitCode).isEqualTo(0)
        } finally {
            timeoutRegistryKey.resetToDefault()
        }
    }

    private fun makeASam(): Path {
        val actualPath = Files.createTempFile("slow_sam", ".bat").toAbsolutePath().toString()

        return SamCommonTestUtils.makeADelayedSam(path = actualPath)
    }
}
