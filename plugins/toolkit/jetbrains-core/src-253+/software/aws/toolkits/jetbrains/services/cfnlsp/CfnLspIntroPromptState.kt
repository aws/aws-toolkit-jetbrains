// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

data class CfnLspIntroPromptStateData(
    var hasResponded: Boolean = false,
)

@Service
@State(name = "cfnLspIntroPromptState", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class CfnLspIntroPromptState : PersistentStateComponent<CfnLspIntroPromptStateData> {
    private var state = CfnLspIntroPromptStateData()

    override fun getState(): CfnLspIntroPromptStateData = state
    override fun loadState(state: CfnLspIntroPromptStateData) { this.state = state }

    fun hasResponded(): Boolean = state.hasResponded
    fun setResponded() { state.hasResponded = true }

    companion object {
        fun getInstance(): CfnLspIntroPromptState = service()
    }
}
