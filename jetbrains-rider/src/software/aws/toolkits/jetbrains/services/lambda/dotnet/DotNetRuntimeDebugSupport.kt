// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugProcessStarter
import software.aws.toolkits.jetbrains.services.lambda.dotnet.DotnetDebugUtils.NUMBER_OF_DEBUG_PORTS
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.RuntimeDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState
import software.aws.toolkits.jetbrains.utils.DotNetDebuggerUtils
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.Step

/**
 * Rider uses it's own DebuggerWorker process that run under .NET with extra parameters to configure:
 *    - debugger mode (client/server)
 *    - frontend port
 *    - backend port
 *
 * Workflow:
 * 1. Find the correct container by filtering `docker ps` for one of the published port of the debugger
 * 2. Use `docker exec` and a shell script to locate the dotnet PID of the runtime.
 * 3. Launch the Debugger worker in server mode using `docker exec`
 * 4. Send the command to the worker to attach to the correct PID.
 */
class DotNetRuntimeDebugSupport : RuntimeDebugSupport {
    override fun numberOfDebugPorts(): Int = NUMBER_OF_DEBUG_PORTS

    override fun samArguments(debugPorts: List<Int>): List<String> = listOf("--debugger-path", DotNetDebuggerUtils.debuggerBinDir.path)
    override fun additionalDebugProcessSteps(environment: ExecutionEnvironment, state: SamRunningState): List<Step> = listOf(FindDockerContainer(), FindPid())

    override suspend fun createDebugProcess(
        environment: ExecutionEnvironment,
        debugHost: String,
        debugPorts: List<Int>,
        context: Context
    ): XDebugProcessStarter = DotnetDebugUtils.createDebugProcess(environment, debugHost, debugPorts, context)
}
