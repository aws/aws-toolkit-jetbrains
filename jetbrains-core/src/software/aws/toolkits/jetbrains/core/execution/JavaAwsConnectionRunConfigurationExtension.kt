// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.ModuleRootManager
import org.jdom.Element
import software.aws.toolkits.resources.message

class JavaAwsConnectionRunConfigurationExtension : RunConfigurationExtension() {
    private val delegate = AwsConnectionRunConfigurationExtension<RunConfigurationBase<*>>()
    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T, params: JavaParameters, runnerSettings: RunnerSettings?) {
        configuration ?: return
        val environment = params.env

        delegate.addEnvironmentVariables(configuration, environment, runtimeString = { determineVersion(configuration) })
    }

    override fun getEditorTitle() = message("aws_connection.tab.label")

    override fun <T : RunConfigurationBase<*>?> createEditor(configuration: T): SettingsEditor<T>? = connectionSettingsEditor(
        configuration
    )

    override fun readExternal(runConfiguration: RunConfigurationBase<*>, element: Element) = delegate.readExternal(runConfiguration, element)

    override fun writeExternal(runConfiguration: RunConfigurationBase<*>, element: Element) = delegate.writeExternal(runConfiguration, element)

    private fun <T> determineVersion(configuration: T): String? = (configuration as? ApplicationConfiguration)?.let {
        configuration.configurationModule?.module
    }?.let {
        ModuleRootManager.getInstance(it).sdk
    }?.let {
        JavaSdk.getInstance().getVersion(it)?.name
    }
}
