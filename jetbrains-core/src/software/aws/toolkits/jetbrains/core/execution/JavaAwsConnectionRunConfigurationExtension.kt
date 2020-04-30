// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.options.SettingsEditor
import org.jdom.Element
import software.aws.toolkits.resources.message

class JavaAwsConnectionRunConfigurationExtension : RunConfigurationExtension() {
    private val delegate = AwsConnectionRunConfigurationExtension<RunConfigurationBase<*>>()
    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T, params: JavaParameters, runnerSettings: RunnerSettings?) {
        configuration ?: return
        val environment = params.env
        delegate.addEnvironmentVariables(configuration, environment)
    }

    override fun getEditorTitle() = message("aws_connection.tab.label")

    override fun <T : RunConfigurationBase<*>?> createEditor(configuration: T): SettingsEditor<T>? = connectionSettingsEditor(
        configuration
    )

    override fun readExternal(runConfiguration: RunConfigurationBase<*>, element: Element) = delegate.readExternal(runConfiguration, element)

    override fun writeExternal(runConfiguration: RunConfigurationBase<*>, element: Element) = delegate.writeExternal(runConfiguration, element)
}
