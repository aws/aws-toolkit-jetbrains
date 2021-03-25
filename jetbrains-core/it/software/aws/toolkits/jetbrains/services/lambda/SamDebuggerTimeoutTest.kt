// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.application.options.RegistryManager
import com.intellij.execution.Output
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import org.assertj.core.api.Assertions.assertThat
import software.aws.toolkits.jetbrains.services.lambda.execution.local.LocalLambdaRunConfiguration
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommonTestUtils
import software.aws.toolkits.jetbrains.utils.executeRunConfigurationAndWait
import software.aws.toolkits.jetbrains.utils.setSamExecutable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

object SamDebuggerTimeoutTest {
    fun `does not timeout if SAM has output`(
        runConfiguration: LocalLambdaRunConfiguration,
        timeout: Duration = Duration.ofSeconds(5),
        runConfigExecUtil: (RunConfiguration, String) -> Output = ::executeRunConfigurationAndWait
    ) {
        val samPath = makeASam()
        setSamExecutable(samPath)

        val timeoutRegistryKey = RegistryManager.getInstance().get("aws.debuggerAttach.timeout")
        try {
            timeoutRegistryKey.setValue(timeout.toMillis().toInt())

            val executeLambda = runConfigExecUtil(runConfiguration, DefaultDebugExecutor.EXECUTOR_ID)
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
