// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.UUID

@State(name = "aws", storages = [Storage("aws.xml")])
class AwsSettings : PersistentStateComponent<AwsConfiguration> {
    private val _lock: Any = Any()
    private var state = AwsConfiguration()

    override fun getState(): AwsConfiguration = state

    override fun loadState(state: AwsConfiguration) {
        this.state = state
    }

    var isTelemetryEnabled: Boolean
        get() = state.isTelemetryEnabled ?: true
        set(value) {
            state.isTelemetryEnabled = value
        }

    var promptedForTelemetry: Boolean
        get() = state.promptedForTelemetry ?: false
        set(value) {
            state.promptedForTelemetry = value
        }

    val clientId: UUID
        get() {
            synchronized(_lock) {
                if (state.clientId == null) {
                    state.clientId = UUID.randomUUID()
                }
            }

            return state.clientId!!
        }

    companion object {
        @JvmStatic
        fun getInstance(): AwsSettings = ServiceManager.getService(AwsSettings::class.java)
    }
}

data class AwsConfiguration(
    var isTelemetryEnabled: Boolean? = null,
    var promptedForTelemetry: Boolean? = null,
    var clientId: UUID? = null
)
