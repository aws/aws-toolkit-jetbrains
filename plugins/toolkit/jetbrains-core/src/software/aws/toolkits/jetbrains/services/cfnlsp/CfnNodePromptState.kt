// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

private const val FIFTEEN_DAYS_MS = 15L * 24 * 60 * 60 * 1000

@Service
@State(name = "cfnNodePromptState", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class CfnNodePromptState : PersistentStateComponent<CfnNodePromptState.State> {
    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    fun shouldPrompt(): Boolean {
        if (state.lastPromptTime == 0L) return true
        return System.currentTimeMillis() - state.lastPromptTime >= FIFTEEN_DAYS_MS
    }

    fun dismissTemporarily() {
        state.lastPromptTime = System.currentTimeMillis()
    }

    class State(
        var lastPromptTime: Long = 0L,
    )

    companion object {
        fun getInstance(): CfnNodePromptState = service()
    }
}
