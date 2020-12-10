// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.goide.dlv.DlvDebugProcess
import com.goide.dlv.DlvDisconnectOption
import com.goide.dlv.DlvRemoteVmConnection
import com.goide.execution.GoRunUtil.getBundledDlv
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.connectRetrying
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.ide.BuiltInServerManager
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
                                val process = DlvDebugProcess(session, DlvRemoteVmConnection(DlvDisconnectOption.DETACH), executionResult, true)

                                val processHandler = executionResult.processHandler
                                val socketAddress = InetSocketAddress(debugHost, debugPorts.first())

                                if (processHandler == null || processHandler.isStartNotified) {
                                    process.connect(socketAddress)
                                } else {
                                    processHandler.addProcessListener(
                                        object : ProcessAdapter() {
                                            override fun startNotified(event: ProcessEvent) {
                                                // TODO if we don't wait, it sometimes connects to the
                                                // container before the debugger starts which then breaks
                                                // TODO what else can we do?
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
        // This can take a target platform, but that pulls directly from GOOS, so we have to walk back up the file tree
        // either way. Goland comes with mac/window/linux dlv since it supports remote debugging, so it is always safe to
        // pull the linux one
        val dlvFolder = getBundledDlv(null)?.parentFile?.parentFile?.resolve("linux")
            ?: throw IllegalStateException("Packaged Devle debugger is not found!")
        // Delve ships with the IDE, but it is not marked executable. The first time the IDE runs it, Delve is set executable
        // At that point. Since we don't know if it's executable or not, we have to set it manually. TODO Is there a better way?
        dlvFolder.resolve("dlv").setExecutable(true, true)
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
