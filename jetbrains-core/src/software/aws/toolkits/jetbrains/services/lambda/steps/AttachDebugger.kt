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
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamDebugSupport
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
        val heartbeatDisposable = Disposer.newDisposable()
        var lastHeartbeat = System.currentTimeMillis()

        val session = runBlocking {
            try {
                val connectJob = async(CoroutineName("SamWaitForDebugProcess")) {
                    val debugPorts = context.getRequiredAttribute(DEBUG_PORTS)
                    val debugProcessStarter = state
                        .settings
                        .resolveDebuggerSupport()
                        .createDebugProcess(context, environment, state, state.settings.debugHost, debugPorts)

                    // always wait until we have a sam invoke process handle before trying to attach
                    val samProcessHandler = context.pollingGet(SamRunnerStep.SAM_PROCESS_HANDLER)
                    val session = withContext(getCoroutineUiContext()) {
                        val debugManager = XDebuggerManager.getInstance(environment.project)
                        // Requires EDT on some paths, so always requires to be run on EDT
                        debugManager.startSessionAndShowTab(environment.runProfile.name, environment.contentToReuse, debugProcessStarter)
                    }

                    samProcessHandler.addProcessListener(buildConsoleOutputProcessAdapter { session.consoleView })
                    samProcessHandler.addProcessListener(
                        object : ProcessAdapter() {
                            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                                lastHeartbeat = System.currentTimeMillis()
                            }
                        },
                        heartbeatDisposable
                    )

                    session
                }

                launch(ExpirableExecutor.on(AppExecutorUtil.getAppExecutorService()).expireWith(heartbeatDisposable).coroutineDispatchingContext()) {
                    while (connectJob.isActive) {
                        if ((System.currentTimeMillis() - lastHeartbeat) > SamDebugSupport.debuggerConnectTimeoutMs()) {
                            throw ExecutionException(message("lambda.debug.process.start.timeout"))
                        }
                        delay(500)
                    }
                }

                connectJob.await()
            } catch (e: TimeoutCancellationException) {
                throw ExecutionException(message("lambda.debug.process.start.timeout"))
            } catch (e: Throwable) {
                LOG.warn(e) { "Failed to start debugger" }
                throw ExecutionException(e)
            } finally {
                Disposer.dispose(heartbeatDisposable)
            }
        }

        // Make sure the session is always cleaned up
        launch {
            while (!context.isCompleted()) {
                delay(100)
            }
            session.stop()
        }
    }

    private companion object {
        val LOG = getLogger<AttachDebugger>()

        fun buildConsoleOutputProcessAdapter(console: (() -> ConsoleView?)) = object : ProcessAdapter() {
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
