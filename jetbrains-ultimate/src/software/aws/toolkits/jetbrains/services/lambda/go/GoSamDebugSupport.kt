// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import software.amazon.awssdk.services.lambda.model.PackageType
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.utils.buildList
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import java.net.InetSocketAddress

class GoSamDebugSupport : SamDebugSupport {
    override fun createDebugProcess(
        environment: ExecutionEnvironment,
        state: SamRunningState,
        debugHost: String,
        debugPorts: List<Int>
    ): XDebugProcessStarter? {
        throw UnsupportedOperationException("Use 'createDebugProcessAsync' instead")
    }

    override fun createDebugProcessAsync(
        environment: ExecutionEnvironment,
        state: SamRunningState,
        debugHost: String,
        debugPorts: List<Int>
    ): Promise<XDebugProcessStarter?> {
        val promise = AsyncPromise<XDebugProcessStarter?>()
        val bgContext = ExpirableExecutor.on(AppExecutorUtil.getAppExecutorService()).expireWith(environment).coroutineDispatchingContext()
        val edtContext = getCoroutineUiContext(ModalityState.any(), environment)

        ApplicationThreadPoolScope(environment.runProfile.name).launch(bgContext) {
            try {
                val executionResult = state.execute(environment.executor, environment.runner)

                withContext(edtContext) {
                    promise.setResult(
                        object : XDebugProcessStarter() {
                            override fun start(session: XDebugSession): XDebugProcess {
                                val process = createDelveDebugProcess(session, executionResult)

                                val processHandler = executionResult.processHandler
                                val socketAddress = InetSocketAddress(debugHost, debugPorts.first())

                                if (processHandler == null || processHandler.isStartNotified) {
                                    process.connect(socketAddress)
                                } else {
                                    processHandler.addProcessListener(
                                        object : ProcessAdapter() {
                                            override fun startNotified(event: ProcessEvent) {
                                                // If we don't wait, then then debugger will try to attach to
                                                // the container before it starts Devle. So, we have to add a sleep.
                                                // Delve takes quite a while to start in the sam cli images hence long sleep
                                                // See https://youtrack.jetbrains.com/issue/GO-10279
                                                // TODO revisit this to see if higher IDE versions help FIX_WHEN_MIN_IS_211 (?)
                                                Thread.sleep(10000)
                                                process.connect(socketAddress)
                                            }
                                        }
                                    )
                                }
                                return process
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                LOG.warn(t) { "Failed to start debugger" }
                promise.setError(t)
            }
        }

        return promise
    }

    override fun samArguments(runtime: Runtime, packageType: PackageType, debugPorts: List<Int>): List<String> = buildList {
        val dlvFolder = getDelve()
        add("--debugger-path")
        add(dlvFolder.absolutePath)
        add("--debug-args")
        if (packageType == PackageType.IMAGE) {
            add(
                "/var/runtime/aws-lambda-go delveAPI -delveAPI=2 -delvePort=${debugPorts.first()} -delvePath=/tmp/lambci_debug_files/dlv"
            )
        } else {
            add("-delveAPI=2")
        }
    }

    private companion object {
        val LOG = getLogger<GoSamDebugSupport>()
    }
}
