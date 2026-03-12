// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.jetbrains.settings.CfnLspSettings
import software.aws.toolkits.resources.AwsToolkitBundle.message

private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
private const val TELEMETRY_DOCS_URL = "https://github.com/aws-cloudformation/cloudformation-languageserver/tree/main/src/telemetry"

@Service
@State(name = "cfnTelemetryPromptState", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class CfnTelemetryPromptState : PersistentStateComponent<CfnTelemetryPromptState.State> {
    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    var hasResponded: Boolean
        get() = state.hasResponded
        set(value) { state.hasResponded = value }

    var lastPromptDate: Long
        get() = state.lastPromptDate
        set(value) { state.lastPromptDate = value }

    class State(
        var hasResponded: Boolean = false,
        var lastPromptDate: Long = 0L,
    )

    companion object {
        fun getInstance(): CfnTelemetryPromptState = service()
    }
}

internal class CfnTelemetryPrompter : ProjectActivity {
    override suspend fun execute(project: Project) {
        val promptState = CfnTelemetryPromptState.getInstance()

        if (promptState.hasResponded) return

        val now = System.currentTimeMillis()
        if (promptState.lastPromptDate != 0L && now - promptState.lastPromptDate < THIRTY_DAYS_MS) return

        showPrompt(project)
    }

    private fun showPrompt(project: Project) {
        val notification = Notification(
            CfnLspExtensionConfig.TELEMETRY_NOTIFICATION_GROUP_ID,
            message("cloudformation.telemetry.prompt.title"),
            message("cloudformation.telemetry.prompt.message"),
            NotificationType.INFORMATION
        )

        notification.addAction(object : NotificationAction(message("cloudformation.telemetry.prompt.action.allow")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                applyChoice(telemetryEnabled = true, permanent = true)
                notification.expire()
            }
        })

        notification.addAction(object : NotificationAction(message("cloudformation.telemetry.prompt.action.not_now")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                applyChoice(telemetryEnabled = false, permanent = false)
                notification.expire()
            }
        })

        notification.addAction(object : NotificationAction(message("cloudformation.telemetry.prompt.action.never")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                applyChoice(telemetryEnabled = false, permanent = true)
                notification.expire()
            }
        })

        notification.addAction(object : NotificationAction(message("cloudformation.telemetry.prompt.action.learn_more")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                BrowserUtil.browse(TELEMETRY_DOCS_URL)
            }
        })

        notification.notify(project)
    }

    private fun applyChoice(telemetryEnabled: Boolean, permanent: Boolean) {
        val promptState = CfnTelemetryPromptState.getInstance()
        val settings = CfnLspSettings.getInstance()

        promptState.hasResponded = permanent
        promptState.lastPromptDate = System.currentTimeMillis()

        settings.isTelemetryEnabled = telemetryEnabled
        settings.notifySettingsChanged()
    }
}
