// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Attribute

@Service
@State(name = "lspSettings", storages = [Storage("aws.xml")])
class LspSettings : PersistentStateComponent<LspConfiguration> {
    private var state = LspConfiguration()

    override fun getState(): LspConfiguration = state

    override fun loadState(state: LspConfiguration) {
        this.state = state
    }

    fun getArtifactPath() = run {
        when {
            state.artifactPath == null -> ""
            else -> state.artifactPath.toString()
        }
    }

    fun setExecutablePath(artifactPath: String?) {
            if (artifactPath == null) {
                state.artifactPath = ""
            } else {
                state.artifactPath = artifactPath
            }
        }

    companion object {
        fun getInstance(): LspSettings = service()
    }
}

data class LspConfiguration(
    @Attribute(value = "path")
    var artifactPath: String? = null,
)
