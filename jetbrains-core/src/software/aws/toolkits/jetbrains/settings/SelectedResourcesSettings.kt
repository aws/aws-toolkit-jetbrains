// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Property

interface SelectedResourcesSettings {
    val selected: Set<String>
    fun selected(selected: Set<String>)

    companion object {
        fun getInstance(): SelectedResourcesSettings = service()
    }
}

@State(name = "dynamic_resources", storages = [Storage("aws.xml")])
internal class DefaultSelectedResourcesSettings : SimplePersistentStateComponent<SelectedResourcesSettingsState>(SelectedResourcesSettingsState()) {
    val selected: Set<String>
        get() = state.value

    fun selected(selected: Set<String>) {
        state.value.clear()
        state.value.addAll(selected)
    }
}

internal class SelectedResourcesSettingsState : BaseState() {
    @get:Property
    val value = mutableSetOf<String>()
}
