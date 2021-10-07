// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.execution.steps

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import java.time.Instant

abstract class CliBasedStep : Step() {
    protected abstract fun constructCommandLine(context: Context): GeneralCommandLine
    protected open fun recordTelemetry(context: Context, startTime: Instant, result: Result) {}
    protected open fun onProcessStart(context: Context, processHandler: ProcessHandler) {}
    protected open fun createProcessEmitter(stepEmitter: StepEmitter): ProcessListener = CliOutputEmitter(stepEmitter)

    protected open fun handleSuccessResult(output: String, messageEmitter: StepEmitter, context: Context) {}

    /**
     * Processes the command's stdout and throws an exception after the CLI exits with failure.
     * @return null if the failure should be ignored. You're probably doing something wrong if you want this.
     */
    protected open fun handleErrorResult(exitCode: Int, output: String, stepEmitter: StepEmitter): Nothing? {
        throw IllegalStateException(message("general.execution.cli_error", exitCode))
    }

    final override fun execute(context: Context, messageEmitter: StepEmitter, ignoreCancellation: Boolean) {
        val startTime = Instant.now()
        var result = Result.Succeeded
        try {
            val commandLine = constructCommandLine(context)

            LOG.debug { "Built command line: ${commandLine.commandLineString}" }

            // Unix: Sends SIGINT on destroy so Docker container is shut down
            // Windows: Run with mediator to allow for Cntrl+C to be used
            val processHandler = object : KillableColoredProcessHandler(commandLine, true) {
                override fun startNotify() {
                    super.startNotify()
                    onProcessStart(context, this)
                }

                override fun doDestroyProcess() {
                    // send signal only if user explicitly requests termination
                    if (this.getUserData(ProcessHandler.TERMINATION_REQUESTED) == true) {
                        super.doDestroyProcess()
                    } else {
                        detachProcess()
                    }
                }
            }
            val processCapture = CapturingProcessAdapter()
            processHandler.addProcessListener(processCapture)
            processHandler.addProcessListener(createProcessEmitter(messageEmitter))
            processHandler.startNotify()

            monitorProcess(processHandler, context, ignoreCancellation)

            if (!ignoreCancellation) {
                context.throwIfCancelled()
            }

            if (processHandler.exitCode == 0) {
                handleSuccessResult(processCapture.output.stdout, messageEmitter, context)
                return
            }

            handleErrorResult(processCapture.output.exitCode, processCapture.output.stdout, messageEmitter)
        } catch (e: ProcessCanceledException) {
            LOG.debug(e) { """Step "$stepName" cancelled!""" }
            result = Result.Cancelled
        } catch (e: Exception) {
            result = Result.Failed
            throw e
        } finally {
            recordTelemetry(context, startTime, result)
        }
    }

    protected class CliOutputEmitter(private val messageEmitter: StepEmitter, private val printStdOut: Boolean = true) : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            LOG.debug {
                val prefix = if (outputType == ProcessOutputTypes.STDERR) {
                    "[STDERR]"
                } else {
                    "[STDOUT]"
                }

                "$prefix ${event.text.trim()}"
            }

            if (outputType == ProcessOutputTypes.STDOUT) {
                if (printStdOut) {
                    messageEmitter.emitMessage(event.text, isError = false)
                }
            } else {
                messageEmitter.emitMessage(event.text, outputType == ProcessOutputTypes.STDERR)
            }
        }
    }

    private fun monitorProcess(processHandler: OSProcessHandler, context: Context, ignoreCancellation: Boolean) {
        while (!processHandler.waitFor(WAIT_INTERVAL_MILLIS)) {
            if (!ignoreCancellation && context.isCancelled()) {
                if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                    processHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)
                    processHandler.destroyProcess()
                }
            }
        }
    }

    private companion object {
        const val WAIT_INTERVAL_MILLIS = 100L
        private val LOG = getLogger<CliBasedStep>()
    }
}
