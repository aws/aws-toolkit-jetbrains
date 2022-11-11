// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam.sync

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.toEnvironmentVariables
import software.aws.toolkits.jetbrains.services.lambda.sam.getSamCli
import java.nio.charset.Charset
import java.nio.file.Path
import javax.swing.Icon

class SyncApplicationRunProfile(
    private val project: Project,
    private val settings: SyncServerlessApplicationSettings,
    private val connection: ConnectionSettings,
    private val templatePath: Path,
    private val syncOnlyCode: Boolean
) : RunProfile {
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = SyncApplicationRunProfileState(environment)

    override fun getName(): String = settings.stackName

    override fun getIcon(): Icon? = null

    inner class SyncApplicationRunProfileState(environment: ExecutionEnvironment) : CommandLineState(environment) {
        val stackName = settings.stackName
        override fun startProcess(): ProcessHandler {
            val processHandler = KillableProcessHandler(getSamSyncCommand())
            ProcessTerminatedListener.attach(processHandler)
            return processHandler
        }

        private fun getSamSyncCommand(): GeneralCommandLine = getSamCli().apply {
            withEnvironment(connection.toEnvironmentVariables())
            withWorkDirectory(templatePath.toAbsolutePath().parent.toString())
            addParameter("sync")
            addParameter("--stack-name")
            addParameter(stackName)
            addParameter("--template-file")
            addParameter(templatePath.toString())
            addParameter("--s3-bucket")
            addParameter(settings.bucket)
            settings.ecrRepo?.let {
                addParameter("--image-repository")
                addParameter(it)
            }
            if (settings.capabilities.isNotEmpty()) {
                addParameter("--capabilities")
                addParameters(settings.capabilities.map { it.capability })
            }
            if (settings.parameters.isNotEmpty()) {
                addParameter("--parameter-overrides")
                settings.parameters.forEach { (key, value) ->
                    addParameter(
                        "${escapeParameter(key)}=${escapeParameter(value)}"
                    )
                }
            }

            if (settings.tags.isNotEmpty()) {
                addParameter("--tags")
                settings.tags.forEach { (key, value) ->
                    addParameter(
                        "${escapeParameter(key)}=${escapeParameter(value)}"
                    )
                }
            }
            addParameter("--no-dependency-layer")
            if (syncOnlyCode) {
                addParameter("--code")
            }
        }

        private fun escapeParameter(param: String): String {
            // Invert the quote if the string is already quoted
            val quote = if (param.startsWith("\"") || param.endsWith("\"")) {
                "'"
            } else {
                "\""
            }

            return quote + param + quote
        }

        override fun execute(executor: Executor, runner: ProgramRunner<*>) = super.execute(executor, runner).apply {
            processHandler?.addProcessListener(object : ProcessAdapter() {

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType === ProcessOutputTypes.STDOUT ||
                        outputType === ProcessOutputTypes.STDERR
                    ) {
                        if (event.text.contains("[Y/n]:")) {
                            insertAssertionNow = true

                            ApplicationManager.getApplication().executeOnPooledThread {
                                while (insertAssertionNow) {
                                    processHandler.processInput?.write("Y\n".toByteArray(Charset.defaultCharset()))
                                }
                            }
                        } else {
                            insertAssertionNow = false
                        }
                        runInEdt {
                            RunContentManager.getInstance(project).toFrontRunContent(executor, processHandler)
                        }
                    }
                }
            })
        }
    }

    companion object {
        private var insertAssertionNow = false
    }
}
