// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.javascript.nodejs.execution.AbstractNodeTargetRunProfile
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.runConfiguration.AbstractNodeRunConfigurationExtension
import com.intellij.javascript.nodejs.execution.runConfiguration.NodeRunConfigurationLaunchSession
import com.intellij.openapi.options.SettingsEditor
import org.jdom.Element
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.experiments.ToolkitExperiment
import software.aws.toolkits.jetbrains.core.experiments.isEnabled
import software.aws.toolkits.resources.message
import java.util.concurrent.TimeUnit

class NodeJsAwsConnectionRunConfigurationExtension : AbstractNodeRunConfigurationExtension() {
    private val delegate = AwsConnectionRunConfigurationExtension<AbstractNodeTargetRunProfile>()
    override fun <P : AbstractNodeTargetRunProfile> createEditor(configuration: P): SettingsEditor<P> = connectionSettingsEditor(configuration)

    override fun getEditorTitle() = message("aws_connection.tab.label")

    override fun createLaunchSession(configuration: AbstractNodeTargetRunProfile, environment: ExecutionEnvironment): NodeRunConfigurationLaunchSession =
        SessionThing(configuration)

    override fun readExternal(runConfiguration: AbstractNodeTargetRunProfile, element: Element) = delegate.readExternal(runConfiguration, element)

    override fun writeExternal(runConfiguration: AbstractNodeTargetRunProfile, element: Element) = delegate.writeExternal(runConfiguration, element)

    override fun isApplicableFor(profile: AbstractNodeTargetRunProfile) = NodeJsAwsConnectionExperiment.isEnabled()

    private inner class SessionThing(private val configuration: AbstractNodeTargetRunProfile) : NodeRunConfigurationLaunchSession() {
        override fun addNodeOptionsTo(targetRun: NodeTargetRun) {
            if (NodeJsAwsConnectionExperiment.isEnabled()) {
                delegate.addToTargetCommandLineBuilder(
                    configuration,
                    targetRun.commandLineBuilder,
                    runtimeString = {
                        tryOrNull {
                            configuration.interpreter?.provideCachedVersionOrFetch()?.blockingGet(500, TimeUnit.MILLISECONDS)?.let { "Node $it" }
                        }
                    }
                )
            }
        }
    }
}

object NodeJsAwsConnectionExperiment : ToolkitExperiment(
    "nodeJsRunConfigurationExtension",
    { message("run_configuration_extension.feature.node.title") },
    { message("run_configuration_extension.feature.node.description") }
)
