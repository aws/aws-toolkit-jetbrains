// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import java.util.prefs.Preferences
import java.util.UUID

@State(name = "aws", storages = [Storage("aws.xml")])
class AwsSettings : PersistentStateComponent<AwsConfiguration> {
    private val preferences = Preferences.userRoot().node(this.javaClass.canonicalName)
    private val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(TELEMETRY_ENABLED_CHANGED)
    private var state = AwsConfiguration()

    init {
        publisher.notify(isTelemetryEnabled)
    }

    override fun getState(): AwsConfiguration = state

    override fun loadState(state: AwsConfiguration) {
        notifyTelemetryEnabledChanged {
            this.state = state
        }
    }

    var isTelemetryEnabled: Boolean
        get() = state.isTelemetryEnabled ?: true
        set(value) {
            notifyTelemetryEnabledChanged {
                state.isTelemetryEnabled = value
            }
        }

    var promptedForTelemetry: Boolean
        get() = state.promptedForTelemetry ?: false
        set(value) {
            state.promptedForTelemetry = value
        }

    val clientId: UUID
        @Synchronized get() = UUID.fromString(preferences.get(CLIENT_ID_KEY, UUID.randomUUID().toString())).also {
            preferences.put(CLIENT_ID_KEY, it.toString())
        }

    private fun notifyTelemetryEnabledChanged(action: () -> Unit) {
        val oldValue: Boolean = isTelemetryEnabled

        try {
            action()
        } finally {
            val newValue: Boolean = isTelemetryEnabled
            if (newValue != oldValue) {
                publisher.notify(newValue)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): AwsSettings = ServiceManager.getService(AwsSettings::class.java)

        const val CLIENT_ID_KEY = "CLIENT_ID"

        val TELEMETRY_ENABLED_CHANGED: Topic<TelemetryEnabledChangedNotifier> = Topic.create(
                "IS_TELEMETRY_ENABLED_CHANGED",
                TelemetryEnabledChangedNotifier::class.java
        )
    }
}

data class AwsConfiguration(
    var isTelemetryEnabled: Boolean? = null,
    var promptedForTelemetry: Boolean? = null
)

interface TelemetryEnabledChangedNotifier {
    fun notify(isTelemetryEnabled: Boolean)
}
