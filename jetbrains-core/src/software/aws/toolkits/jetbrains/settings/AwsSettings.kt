// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.resources.message
import java.util.UUID
import java.util.prefs.Preferences

interface AwsSettings {
    var isTelemetryEnabled: Boolean
    var promptedForTelemetry: Boolean
    var useDefaultCredentialRegion: UseAwsCredentialRegion
    val clientId: UUID

    companion object {
        @JvmStatic
        fun getInstance(): AwsSettings = ServiceManager.getService(AwsSettings::class.java)
    }
}

enum class UseAwsCredentialRegion(private val description: String) {
    Always(message("settings.credentials.prompt_for_default_region_switch.always.description")),
    Prompt(message("settings.credentials.prompt_for_default_region_switch.ask.description")),
    Never(message("settings.credentials.prompt_for_default_region_switch.never.description"));

    override fun toString(): String = description
}

@State(name = "aws", storages = [Storage("aws.xml")])
class DefaultAwsSettings : PersistentStateComponent<AwsConfiguration>, AwsSettings {
    private val preferences = Preferences.userRoot().node(this.javaClass.canonicalName)
    private var state = AwsConfiguration()

    override fun getState(): AwsConfiguration = state

    override fun loadState(state: AwsConfiguration) {
        this.state = state
    }

    override var isTelemetryEnabled: Boolean
        get() = state.isTelemetryEnabled ?: true
        set(value) {
            state.isTelemetryEnabled = value
            TelemetryService.getInstance().setTelemetryEnabled(value)
        }

    override var promptedForTelemetry: Boolean
        get() = state.promptedForTelemetry ?: false
        set(value) {
            state.promptedForTelemetry = value
        }

    override var useDefaultCredentialRegion: UseAwsCredentialRegion
        get() = state.useDefaultCredentialRegion?.let { UseAwsCredentialRegion.valueOf(it) } ?: UseAwsCredentialRegion.Prompt
        set(value) {
            state.useDefaultCredentialRegion = value.name
        }

    override val clientId: UUID
        @Synchronized get() = UUID.fromString(preferences.get(CLIENT_ID_KEY, UUID.randomUUID().toString())).also {
            preferences.put(CLIENT_ID_KEY, it.toString())
        }

    companion object {
        const val CLIENT_ID_KEY = "CLIENT_ID"
    }
}

data class AwsConfiguration(
    var isTelemetryEnabled: Boolean? = null,
    var promptedForTelemetry: Boolean? = null,
    var useDefaultCredentialRegion: String? = null
)

class ShowSettingsAction : AnAction(message("aws.settings.show.label")), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.getRequiredData(LangDataKeys.PROJECT), AwsSettingsConfigurable::class.java)
    }
}
