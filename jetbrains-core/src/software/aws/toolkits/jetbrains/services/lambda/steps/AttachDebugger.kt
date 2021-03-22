// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.steps

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamDebugSupport.Companion.debuggerConnectTimeoutMs
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.resolveDebuggerSupport
import software.aws.toolkits.jetbrains.services.lambda.steps.GetPorts.Companion.DEBUG_PORTS
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.MessageEmitter
import software.aws.toolkits.jetbrains.utils.execution.steps.Step
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message

class AttachDebugger(
    val environment: ExecutionEnvironment,
    val state: SamRunningState
) : Step(), CoroutineScope by ApplicationThreadPoolScope("AttachSamDebugger") {
    override val stepName = message("sam.debug.attach")
    override val hidden = false

    override fun execute(context: Context, messageEmitter: MessageEmitter, ignoreCancellation: Boolean) {
        runBlocking {
            try {
                val debugPorts = context.getRequiredAttribute(DEBUG_PORTS)
                withTimeout(debuggerConnectTimeoutMs()) {
                    val debugProcessStarter = state
                        .settings
                        .resolveDebuggerSupport()
                        .createDebugProcess(environment, state.settings.debugHost, debugPorts, context)
                    val session = runBlocking(getCoroutineUiContext()) {
                        val debugManager = XDebuggerManager.getInstance(environment.project)
                        // Requires EDT on some paths, so always requires to be run on EDT
                        debugManager.startSessionAndShowTab(environment.runProfile.name, environment.contentToReuse, debugProcessStarter)
                    }
                    context.blockingGet(SamRunnerStep.SAM_PROCESS_HANDLER).addProcessListener(buildProcessAdapter { session.consoleView })
                    launch {
                        while (!context.isCompleted()) {
                            delay(100)
                        }
                        session.stop()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw ExecutionException(message("lambda.debug.process.start.timeout"))
            } catch (e: Throwable) {
                LOG.warn(e) { "Failed to start debugger" }
                throw e
            }
        }
    }

    private companion object {
        val LOG = getLogger<AttachDebugger>()
        fun buildProcessAdapter(console: (() -> ConsoleView?)) = object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                // Skip system messages
                if (outputType == ProcessOutputTypes.SYSTEM) {
                    return
                }
                val viewType = if (outputType == ProcessOutputTypes.STDERR) {
                    ConsoleViewContentType.ERROR_OUTPUT
                } else {
                    ConsoleViewContentType.NORMAL_OUTPUT
                }
                console()?.print(event.text, viewType)
            }
        }
    }
}
