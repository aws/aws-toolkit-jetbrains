// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.experiments

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Used to control the state of an experimental feature.
 *
 * Use the `aws.toolkit.experiment` extensionpoint to register experiments. This surfaces the configuration in the AWS Settings panel - and in sub-menus.
 *
 * `ToolkitExperiment` implementations should be an `object` for example:
 *
 * ```
 * object MyExperiment : ToolkitExperiment() {..}
 * ```
 *
 * This allows simple use at branch-points for the experiment via the available extension functions:
 *
 * ```
 * if (MyExperiment.isEnabled()) {
 *   // surface experience
 * }
 * ```
 */
abstract class ToolkitExperiment(
    internal val id: String,
    internal val title: () -> String,
    internal val description: () -> String,
    internal val hidden: Boolean = false
) {
    override fun equals(other: Any?) = (other as? ToolkitExperiment)?.id?.equals(id) == true
    override fun hashCode() = id.hashCode()
}

fun ToolkitExperiment.isEnabled(): Boolean = ToolkitExperimentManager.getInstance().isEnabled(this)
internal fun ToolkitExperiment.setState(enabled: Boolean) = ToolkitExperimentManager.getInstance().setState(this, enabled)

@State(name = "experiments", storages = [Storage("aws.xml")])
internal class ToolkitExperimentManager : PersistentStateComponent<Set<String>> {
    private val enabledState = mutableSetOf<String>()
    fun isEnabled(experiment: ToolkitExperiment): Boolean =
        (experiment.id in enabledState || isEnabledByProperty(experiment.id)) && EP_NAME.extensionList.contains(experiment)

    fun setState(experiment: ToolkitExperiment, enabled: Boolean) {
        when (enabled) {
            true -> enabledState.add(experiment.id)
            false -> enabledState.remove(experiment.id)
        }
    }

    override fun getState(): Set<String> = enabledState

    override fun loadState(state: Set<String>) {
        enabledState.clear()
        enabledState.addAll(state)
    }

    companion object {
        internal val EP_NAME = ExtensionPointName.create<ToolkitExperiment>("aws.toolkit.experiment")
        internal fun getInstance(): ToolkitExperimentManager = service()
        internal fun visibileExperiments(): List<ToolkitExperiment> = EP_NAME.extensionList.filterNot { it.hidden }
        private fun isEnabledByProperty(id: String) = System.getProperty("aws.experiment.$id")?.let { it != "false" } ?: false
    }
}
