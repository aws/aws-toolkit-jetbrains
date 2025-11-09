// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.text.nullize

@Service
@State(name = "lspSettings", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
class LspSettings : PersistentStateComponent<LspConfiguration> {
    private var state = LspConfiguration()

    override fun getState(): LspConfiguration = state

    override fun loadState(state: LspConfiguration) {
        this.state = state
    }

    fun getArtifactPath() = state.artifactPath

    fun getNodeRuntimePath() = state.nodeRuntimePath

    fun isCpuProfilingEnabled() = state.cpuProfilingEnabled

    fun setArtifactPath(artifactPath: String?) {
        state.artifactPath = artifactPath.nullize(nullizeSpaces = true)
    }

    fun setNodeRuntimePath(nodeRuntimePath: String?) {
        state.nodeRuntimePath = nodeRuntimePath.nullize(nullizeSpaces = true)
    }

    fun setCpuProfilingEnabled(enabled: Boolean) {
        state.cpuProfilingEnabled = enabled
    }

    companion object {
        fun getInstance(): LspSettings = service()
    }
}

class LspConfiguration : BaseState() {
    var artifactPath by string()
    var nodeRuntimePath by string()
    var cpuProfilingEnabled by property(false)
}
