// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "local_debug", storages = [Storage("aws.xml")])
class LocalDebugSettings : PersistentStateComponent<LocalDebugConfiguration> {
    private var state = LocalDebugConfiguration()

    override fun getState(): LocalDebugConfiguration? = state

    override fun loadState(state: LocalDebugConfiguration) {
        this.state = state
    }

    var localDebugHost: String?
        get() = state.localDebugHost
        set(value) {
            state.localDebugHost = value
        }

    companion object {
        @JvmStatic
        fun getInstance(): LocalDebugSettings = ServiceManager.getService(LocalDebugSettings::class.java)
    }
}

data class LocalDebugConfiguration(
    var localDebugHost: String? = null
)
