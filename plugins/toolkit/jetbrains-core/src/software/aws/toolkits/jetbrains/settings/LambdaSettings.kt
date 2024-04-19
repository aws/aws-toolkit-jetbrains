// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "lambda", storages = [Storage("aws.xml")])
class LambdaSettings : PersistentStateComponent<LambdaConfiguration> {
    private var state = LambdaConfiguration()

    override fun getState(): LambdaConfiguration = state

    override fun loadState(state: LambdaConfiguration) {
        this.state = state
    }

    var showAllHandlerGutterIcons: Boolean
        get() = state.showAllHandlerGutterIcons
        set(value) {
            state.showAllHandlerGutterIcons = value
            ApplicationManager.getApplication().messageBus.syncPublisher(LambdaSettingsChangeListener.TOPIC)
                .samShowAllHandlerGutterIconsSettingsChange(value)
        }

    companion object {
        @JvmStatic
        fun getInstance(): LambdaSettings = service()
    }
}

data class LambdaConfiguration(
    var showAllHandlerGutterIcons: Boolean = false
)
