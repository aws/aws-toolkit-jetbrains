// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.javascript.debugger.CommandLineDebugConfigurator
import com.intellij.javascript.nodejs.NodeFileTransfer
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterChangeListener
import com.intellij.openapi.options.SettingsEditor
import com.intellij.util.Consumer
import com.jetbrains.nodejs.run.NodeJSRunConfigurationExtension
import com.jetbrains.nodejs.run.NodeJSRuntimeSession
import com.jetbrains.nodejs.run.NodeJsRunConfiguration
import org.jdom.Element
import software.aws.toolkits.resources.message

class NodeJsAwsConnectionRunConfigurationExtension : NodeJSRunConfigurationExtension() {
    private val base = AwsConnectionRunConfigurationExtension<NodeJsRunConfiguration>()
    override fun isApplicableFor(configuration: NodeJsRunConfiguration): Boolean = true

    override fun isEnabledFor(applicableConfiguration: NodeJsRunConfiguration, runnerSettings: RunnerSettings?): Boolean = true

    override fun createLocalRuntimeSession(configuration: NodeJsRunConfiguration, environment: ExecutionEnvironment): NodeJSRuntimeSession? =
        GeneralCommandLineInjector(configuration)

    override fun createRemoteRuntimeSession(
        configuration: NodeJsRunConfiguration,
        environment: ExecutionEnvironment,
        fileTransfer: NodeFileTransfer
    ): NodeJSRuntimeSession? = null

    override fun createEditor(
        configuration: NodeJsRunConfiguration,
        listenerRegistrar: Consumer<NodeJsInterpreterChangeListener>?
    ): SettingsEditor<NodeJsRunConfiguration>? = connectionSettingsEditor(configuration)

    override fun readExternal(runConfiguration: NodeJsRunConfiguration, element: Element) = base.readExternal(runConfiguration, element)

    override fun writeExternal(runConfiguration: NodeJsRunConfiguration, element: Element) = base.writeExternal(runConfiguration, element)

    override fun getEditorTitle() = message("aws_connection.tab.label")

    inner class GeneralCommandLineInjector(private val configuration: NodeJsRunConfiguration) : NodeJSRuntimeSession {
        override fun createProcessHandler(
            commandLine: GeneralCommandLine?,
            debugConfigurator: CommandLineDebugConfigurator?,
            openPorts: MutableList<Int>?
        ): ProcessHandler? {
            commandLine?.let {
                base.addEnvironmentVariables(configuration, it)
            }
            return null
        }
    }
}
