// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic

fun interface CdkLspSettingsChangeListener {
    fun settingsChanged()

    companion object {
        val TOPIC = Topic.create("CDK LSP Settings Changed", CdkLspSettingsChangeListener::class.java)
    }
}

@Service
@State(name = "cdkLspSettings", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class CdkLspSettings : PersistentStateComponent<CdkLspSettings.State> {
    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    fun notifySettingsChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(CdkLspSettingsChangeListener.TOPIC)
            .settingsChanged()
    }

    /** Absolute path to the `cdk` CLI. Empty = auto-discover (node_modules, then PATH). */
    var cliPath: String
        get() = state.cliPath
        set(value) { state.cliPath = value }

    /** Directory of the CDK app (folder with cdk.json). Empty = auto-detect. */
    var appDir: String
        get() = state.appDir
        set(value) { state.appDir = value }

    data class State(
        var cliPath: String = "",
        var appDir: String = "",
    )

    companion object {
        fun getInstance(): CdkLspSettings = service()
    }
}
