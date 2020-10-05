// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.rd.defineNestedLifetime
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.net.NetUtils
import com.intellij.util.text.nullize
import com.intellij.xdebugger.XDebugProcessStarter
import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.Serializers
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.put
import com.jetbrains.rd.util.reactive.adviseUntil
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.debugger.RiderDebuggerWorkerModelManager
import com.jetbrains.rider.model.debuggerWorker.DotNetCoreAttachStartInfo
import com.jetbrains.rider.model.debuggerWorker.DotNetDebuggerSessionModel
import com.jetbrains.rider.model.debuggerWorkerConnectionHelperModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.IDebuggerOutputListener
import com.jetbrains.rider.run.bindToSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import software.aws.toolkits.jetbrains.services.lambda.execution.local.SamDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.local.SamRunningState
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.DotNetDebuggerUtils
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.spinUntilResult
import software.aws.toolkits.resources.message
import java.net.InetAddress
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.schedule

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
class DotNetSamDebugSupport : SamDebugSupport {
    companion object {
        private const val DEBUGGER_MODE = "server"

        private const val REMOTE_DEBUGGER_DIR = "/tmp/lambci_debug_files"
        private const val REMOTE_NETCORE_CLI_PATH = "/var/lang/bin/dotnet"
        private const val NUMBER_OF_DEBUG_PORTS = 2

        private const val FIND_PID_SCRIPT =
            """
                for i in `ls /proc/*/exe` ; do
                    symlink=`readlink  ${'$'}i 2>/dev/null`;
                    if [[ "${'$'}symlink" == *"/dotnet" ]]; then
                        echo  ${'$'}i | sed -n 's/.*\/proc\/\(.*\)\/exe.*/\1/p'
                    fi;
                done;
            """
    }

    override fun getDebugPorts(): List<Int> = NetUtils.findAvailableSocketPorts(NUMBER_OF_DEBUG_PORTS).toList()

    override fun patchCommandLine(debugPorts: List<Int>, commandLine: GeneralCommandLine) {
        commandLine.withParameters("--debugger-path")
            .withParameters(DotNetDebuggerUtils.debuggerBinDir.path)

        super.patchCommandLine(debugPorts, commandLine)
    }

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
        val frontendPort = debugPorts[0]
        val backendPort = debugPorts[1]
        val promise = AsyncPromise<XDebugProcessStarter?>()

        // Define a debugger lifetime to be able to dispose the debugger process and all nested component on termination
        // Using the environment as the disposable root seems to make 2nd usage of debug to fail since the lifetime is already terminated
        val debuggerLifetimeDefinition = environment.project.defineNestedLifetime()
        val debuggerLifetime = debuggerLifetimeDefinition.lifetime

        val scheduler = RdDispatcher(debuggerLifetime)

        val debugHostAddress = InetAddress.getByName(debugHost)

        val protocol = Protocol(
            name = environment.runProfile.name,
            serializers = Serializers(),
            identity = Identities(IdKind.Client),
            scheduler = scheduler,
            wire = SocketWire.Client(debuggerLifetime, scheduler, hostAddress = debugHostAddress, port = frontendPort, optId = "FrontendToDebugWorker"),
            lifetime = debuggerLifetime
        )

        val workerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(debuggerLifetime, protocol)

        val executionResult = state.execute(environment.executor, environment.runner)
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console
        val samProcessHandle = executionResult.processHandler
        console.attachToProcess(samProcessHandle)

        // If we have not started the process's notification system, start it now.
        // This is needed to pipe the SAM output to the Console view of the debugger panel
        if (!samProcessHandle.isStartNotified) {
            samProcessHandle.startNotify()
        }

        val bgContext = ExpirableExecutor.on(AppExecutorUtil.getAppExecutorService()).expireWith(environment).coroutineDispatchingContext()
        val edtContext = getCoroutineUiContext(ModalityState.any(), environment)

        ApplicationThreadPoolScope(environment.runProfile.name).launch(bgContext) {
            try {
                val dockerContainer = findDockerContainer(frontendPort)
                val pid = findDotnetPid(dockerContainer)
                val riderDebuggerProcessHandler = startDebugWorker(dockerContainer, backendPort, frontendPort)

                // Link worker process to SAM process lifetime
                samProcessHandle.addProcessListener(
                    object : ProcessAdapter() {
                        override fun processTerminated(event: ProcessEvent) {
                            runInEdt {
                                debuggerLifetimeDefinition.terminate()
                                riderDebuggerProcessHandler.destroyProcess()
                            }
                        }
                    },
                    environment
                )

                withContext(edtContext) {
                    protocol.wire.connected.adviseUntil(debuggerLifetime) connected@{ isConnected ->
                        if (!isConnected) {
                            return@connected false
                        }

                        workerModel.initialized.adviseUntil(debuggerLifetime) initialized@{ isInitialized ->
                            if (!isInitialized) {
                                return@initialized false
                            }

                            // Fire backend to connect to debugger.
                            environment.project.solution.debuggerWorkerConnectionHelperModel.ports.put(
                                debuggerLifetime,
                                environment.executionId,
                                backendPort
                            )

                            val startInfo = DotNetCoreAttachStartInfo(
                                processId = pid,
                                needToBeInitializedImmediately = true
                            )

                            val sessionModel = DotNetDebuggerSessionModel(startInfo)
                            sessionModel.sessionProperties.bindToSettings(debuggerLifetime).apply {
                                enableHeuristicPathResolve.set(true)
                            }

                            workerModel.activeSession.set(sessionModel)

                            promise.setResult(
                                DotNetDebuggerUtils.createAndStartSession(
                                    executionConsole = console,
                                    env = environment,
                                    sessionLifetime = debuggerLifetime,
                                    processHandler = samProcessHandle,
                                    protocol = protocol,
                                    sessionModel = sessionModel,
                                    outputEventsListener = object : IDebuggerOutputListener {}
                                )
                            )

                            return@initialized true
                        }

                        return@connected true
                    }
                }
            } catch (t: Throwable) {
                debuggerLifetimeDefinition.terminate(true)
                promise.setError(t)
            }
        }

        val checkDebuggerTask = Timer("Debugger Worker launch timer", true).schedule(debuggerAttachTimeoutMs) {
            if (debuggerLifetimeDefinition.isAlive && !protocol.wire.connected.value) {
                runInEdt {
                    debuggerLifetimeDefinition.terminate()
                    promise.setError(message("lambda.debug.process.start.timeout"))
                }
            }
        }

        debuggerLifetime.onTermination {
            checkDebuggerTask.cancel()
        }

        return promise
    }

    private suspend fun findDockerContainer(frontendPort: Int): String = spinUntilResult(Duration.ofSeconds(30)) {
        ExecUtil.execAndGetOutput(
            GeneralCommandLine(
                "docker",
                "ps",
                "-q",
                "-f",
                "publish=$frontendPort"
            )
        ).stdout.trim().nullize()
    }

    private suspend fun findDotnetPid(dockerContainer: String): Int = spinUntilResult(Duration.ofSeconds(30)) {
        ExecUtil.execAndGetOutput(
            GeneralCommandLine(
                "docker",
                "exec",
                "-i",
                dockerContainer,
                "/bin/sh",
                "-c",
                FIND_PID_SCRIPT
            )
        ).stdout.trim().nullize()
    }.toInt()

    private fun startDebugWorker(dockerContainer: String, backendPort: Int, frontendPort: Int): OSProcessHandler {
        val runDebuggerCommand = GeneralCommandLine(
            "docker",
            "exec",
            "-i",
            dockerContainer,
            REMOTE_NETCORE_CLI_PATH,
            "$REMOTE_DEBUGGER_DIR/${DotNetDebuggerUtils.debuggerAssemblyFile.name}",
            "--mode=$DEBUGGER_MODE",
            "--frontend-port=$frontendPort",
            "--backend-port=$backendPort"
        )

        return ProcessHandlerFactory.getInstance().createProcessHandler(runDebuggerCommand)
    }
}
