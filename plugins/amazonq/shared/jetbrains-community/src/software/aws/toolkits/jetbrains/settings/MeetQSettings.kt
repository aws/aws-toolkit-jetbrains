// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(name = "meetQPage", storages = [Storage("amazonq.xml", roamingType = RoamingType.DISABLED)])
class MeetQSettings : PersistentStateComponent<MeetQSettingsConfiguration> {
    private var state = MeetQSettingsConfiguration()
    override fun getState(): MeetQSettingsConfiguration? = state

    override fun loadState(state: MeetQSettingsConfiguration) {
        this.state = state
    }

    var shouldDisplayPage: Boolean
        get() = state.shouldDisplayPage
        set(value) {
            state.shouldDisplayPage = value
        }

    var reinvent2024OnboardingCount: Int
        get() = state.reinvent2024OnboardingCount
        set(value) {
            state.reinvent2024OnboardingCount = value
        }

    var disclaimerAcknowledged: Boolean
        get() = state.disclaimerAcknowledged
        set(value) {
            state.disclaimerAcknowledged = value
        }

    var pairProgrammingAcknowledged: Boolean
        get() = state.pairProgrammingAcknowledged
        set(value) {
            state.pairProgrammingAcknowledged = value
        }

    companion object {
        fun getInstance(): MeetQSettings = service()
    }
}
data class MeetQSettingsConfiguration(
    var shouldDisplayPage: Boolean = true,
    var reinvent2024OnboardingCount: Int = 0,
    var disclaimerAcknowledged: Boolean = false,
    var pairProgrammingAcknowledged: Boolean = false,
)
