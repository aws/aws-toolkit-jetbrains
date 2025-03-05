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
import com.intellij.util.xmlb.annotations.Property

@Service
@State(name = "lspSettings", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
class LspSettings : PersistentStateComponent<LspConfiguration> {
    private var state = LspConfiguration()

    override fun getState(): LspConfiguration = state

    override fun loadState(state: LspConfiguration) {
        this.state = state
    }

    fun getArtifactPath() = state.artifactPath

    fun setArtifactPath(artifactPath: String?) {
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

class LspConfiguration : BaseState() {
    @get:Property
    var artifactPath: String = ""
}
