// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.rider.utils

import com.intellij.execution.Output
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.jetbrains.rdclient.util.idea.waitAndPump
import software.aws.toolkits.jetbrains.utils.createRunConfiguration
import java.time.Duration

fun executeRunConfigurationRider(runConfiguration: RunConfiguration, executorId: String = DefaultRunExecutor.EXECUTOR_ID): Output {
    val executeLambda = createRunConfiguration(runConfiguration, executorId)
    // 4 is arbitrary, but Image-based functions can take > 3 min on first build/run, so 4 is a safe number
    waitAndPump(Duration.ofMinutes(4), { executeLambda.isDone })
    if (!executeLambda.isDone) {
        throw IllegalStateException("Took too long to execute Rider run configuration!")
    }
    return executeLambda.get()
}
