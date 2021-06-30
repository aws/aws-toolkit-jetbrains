// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.macro.Macro
import com.intellij.ide.macro.MacroManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.tools.FilterInfo
import com.intellij.tools.Tool
import com.intellij.tools.ToolProcessAdapter
import com.intellij.tools.ToolRunProfile
import com.intellij.tools.ToolsCustomizer
import java.io.File
import javax.swing.Icon

class RunCommandRunProfile(private val tool: Tool, private val dataContext: DataContext, private val credentials: Map<String, String>) : RunProfile {
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val cmdLine = getCommandLine()
        val project = environment.project
        if (cmdLine == null) {
            return null
        }
        val commandLineState = CmdLineState(environment, cmdLine, dataContext, project, tool)
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        val outputFilters: Array<FilterInfo> = tool.outputFilters
        for (outputFilter in outputFilters) {
            builder.addFilter(RegexpFilter(project, outputFilter.regExp))
        }

        commandLineState.consoleBuilder = builder
        return commandLineState
    }

    override fun getName(): String = ToolRunProfile.expandMacrosInName(tool, dataContext)

    override fun getIcon(): Icon? = null

    fun getCommandLine(): GeneralCommandLine? {
        if (StringUtil.isEmpty(tool.workingDirectory)) {
            tool.workingDirectory = ("\$ProjectFileDir$")
        }

        val commandLine = if (Registry.`is`("use.tty.for.external.tools", false)) PtyCommandLine().withConsoleMode(true) else GeneralCommandLine()
        try {
            val parameterString = MacroManager.getInstance().expandMacrosInString(tool.parameters, true, dataContext)
            val workingDirectory = MacroManager.getInstance().expandMacrosInString(tool.workingDirectory, true, dataContext)
            var exePath = MacroManager.getInstance().expandMacrosInString(tool.program, true, dataContext)
            commandLine.parametersList.addParametersString(
                MacroManager.getInstance().expandMacrosInString(parameterString, false, dataContext)
            )
            val workingDirectoryExpanded = MacroManager.getInstance().expandMacrosInString(workingDirectory, false, dataContext)
            if (!StringUtil.isEmpty(workingDirectoryExpanded)) {
                commandLine.setWorkDirectory(workingDirectoryExpanded)
            }
            exePath = MacroManager.getInstance().expandMacrosInString(exePath, false, dataContext)
            if (exePath == null) return null
            val exeFile = File(exePath)
            if (exeFile.isDirectory && exeFile.name.endsWith(".app")) {
                commandLine.exePath = "open"
                commandLine.parametersList.prependAll("-a", exePath)
            } else {
                commandLine.exePath = exePath
            }
        } catch (ignored: Macro.ExecutionCancelledException) {
            return null
        }
        commandLine.withEnvironment(credentials)
        return ToolsCustomizer.customizeCommandLine(commandLine, dataContext)
    }
}

class CmdLineState(
    environment: ExecutionEnvironment,
    private val cmdLine: GeneralCommandLine,
    private val dataContext: DataContext,
    private val project: Project,
    private val tool: Tool
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val processHandler = ColoredProcessHandler(cmdLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val result = super.execute(executor, runner)
        val processHandler = result.processHandler
        if (processHandler != null) {
            processHandler.addProcessListener(
                ToolProcessAdapter(project, tool.synchronizeAfterExecution(), ToolRunProfile.expandMacrosInName(tool, dataContext))
            )
            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType === ProcessOutputTypes.STDOUT && tool.isShowConsoleOnStdOut ||
                        outputType === ProcessOutputTypes.STDERR && tool.isShowConsoleOnStdErr
                    ) {
                        RunContentManager.getInstance(project).toFrontRunContent(executor, processHandler)
                    }
                }
            })
        }
        return result
    }
}
