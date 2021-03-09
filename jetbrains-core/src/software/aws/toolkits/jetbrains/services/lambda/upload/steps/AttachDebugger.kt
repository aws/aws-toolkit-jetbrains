// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload.steps

import com.intellij.execution.RunManager
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.rd.util.spinUntil
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.AsyncPromise
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.clouddebug.DebuggerSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.ImageTemplateRunSettings
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.LocalLambdaRunSettings
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.RuntimeDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamDebugger
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.ZipSettings
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.GetPorts.Companion.DEBUG_PORTS
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.MessageEmitter
import software.aws.toolkits.jetbrains.utils.execution.steps.Step
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

class AttachDebugger(val environment: ExecutionEnvironment, val state: SamRunningState) : Step() {
    override val stepName = ""
    override val hidden = true

    private val edtContext = getCoroutineUiContext()

    override fun execute(context: Context, messageEmitter: MessageEmitter, ignoreCancellation: Boolean) {
        //Thread.sleep(10000)
        val debugPorts = context.getRequiredAttribute(DEBUG_PORTS)
        val runSettings =
            RunManager.getInstance(environment.project).createConfiguration(environment.runProfile.name + "debug", RemoteConfigurationType::class.java)
        runSettings.isActivateToolWindowBeforeRun = false
        // hack in case the user modified their Java Remote configuration template
        runSettings.configuration.beforeRunTasks = emptyList()

        (runSettings.configuration as RemoteConfiguration).apply {
            HOST = DebuggerSupport.LOCALHOST_NAME
            PORT =  debugPorts.first().toString()
            USE_SOCKET_TRANSPORT = true
            SERVER_MODE = false
        }

         DebuggerSupport.executeConfiguration(environment, runSettings).get()
        /*
        val promise = AsyncPromise<RunContentDescriptor>()
        val debugPorts = context.getRequiredAttribute(DEBUG_PORTS)

        var isDebuggerAttachDone = false

        // In integration tests this will block for 1 minute per integration test that uses the debugger because we
        // run integration tests under edt. In real execution, there's some funky thread switching that leads this call
        // to not be on edt, but that is not emulated in tests. So, skip this entirely if we are in unit test mode.
        // Tests have their own timeout which will prevent it running forever without attaching
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(environment.project, message("lambda.debug.waiting"), false) {
                    override fun run(indicator: ProgressIndicator) {
                        val debugAttachedResult = spinUntil(SamDebugger.debuggerConnectTimeoutMs()) { isDebuggerAttachDone }
                        if (!debugAttachedResult) {
                            val message = message("lambda.debug.attach.fail")
                            LOG.error { message }
                            notifyError(message("lambda.debug.attach.error"), message, environment.project)
                        }
                    }
                }
            )
        }

        resolveDebuggerSupport(state.settings).createDebugProcessAsync(environment, state, state.settings.debugHost, debugPorts)
            .onSuccess { debugProcessStarter ->
                val debugManager = XDebuggerManager.getInstance(environment.project)
                val runContentDescriptor = runBlocking(edtContext) {
                    if (debugProcessStarter == null) {
                        null
                    } else {
                        // Requires EDT on some paths, so always requires to be run on EDT
                        debugManager.startSession(environment, debugProcessStarter).runContentDescriptor
                    }
                }
                if (runContentDescriptor == null) {
                    promise.setError(IllegalStateException("Failed to create debug process"))
                } else {
                    promise.setResult(runContentDescriptor)
                }
            }
            .onError {
                promise.setError(it)
            }
            .onProcessed {
                isDebuggerAttachDone = true
            }*/
    }

    // TODO dedupe
    private fun resolveDebuggerSupport(settings: LocalLambdaRunSettings) = when (settings) {
        is ImageTemplateRunSettings -> settings.imageDebugger
        is ZipSettings -> RuntimeDebugSupport.getInstance(settings.runtimeGroup)
        else -> throw IllegalStateException("Can't find debugger support for $settings")
    }

    companion object {
        private val LOG = getLogger<AttachDebugger>()
    }
}
