// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.gettingstarted

import com.intellij.configurationStore.getPersistentStateComponentStorageLocation
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.exists
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.tryOrNull
import software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.SourceOfEntry
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getConnectionCount
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getEnabledConnections
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.GettingStartedPanel
import software.aws.toolkits.jetbrains.settings.GettingStartedSettings
import software.aws.toolkits.telemetry.AuthTelemetry
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.Telemetry

class GettingStartedOnStartup : StartupActivity {
    override fun runActivity(project: Project) {
        try {
            val hasStartedToolkitBefore = tryOrNull {
                getPersistentStateComponentStorageLocation(GettingStartedSettings::class.java)?.exists()
            } ?: true

            if (hasStartedToolkitBefore && CredentialManager.getInstance().getCredentialIdentifiers().isNotEmpty()) {
                GettingStartedSettings.getInstance().shouldDisplayPage = false
            }

            val settings = GettingStartedSettings.getInstance()
            if (!settings.shouldDisplayPage) {
                return
            } else {
                GettingStartedPanel.openPanel(project, firstInstance = true, connectionInitiatedFromExplorer = false)
                Telemetry.auth.addConnection.use {
                    it.source(SourceOfEntry.FIRST_STARTUP.toString())
                        .featureId(FeatureId.Unknown)
                        .credentialSourceId(CredentialSourceId.Unknown)
                        .isAggregated(true)
                        .result(MetricResult.Succeeded)
                        .isReAuth(false)
                }
                AuthTelemetry.addedConnections(
                    project,
                    source = SourceOfEntry.FIRST_STARTUP.toString(),
                    authConnectionsCount = getConnectionCount(),
                    newAuthConnectionsCount = 0,
                    enabledAuthConnections = getEnabledConnections(project),
                    newEnabledAuthConnections = "",
                    attempts = 1,
                    result = Result.Succeeded
                )
                settings.shouldDisplayPage = false
            }
        } catch (e: Exception) {
            LOG.error(e) { "Error opening getting started panel" }
            Telemetry.auth.addConnection.use {
                it.source(SourceOfEntry.FIRST_STARTUP.toString())
                    .featureId(FeatureId.Unknown)
                    .credentialSourceId(CredentialSourceId.Unknown)
                    .isAggregated(false)
                    .result(MetricResult.Failed)
                    .reason("Error opening getting started panel")
                    .isReAuth(false)
            }
            AuthTelemetry.addedConnections(
                project,
                source = SourceOfEntry.FIRST_STARTUP.toString(),
                authConnectionsCount = getConnectionCount(),
                newAuthConnectionsCount = 0,
                enabledAuthConnections = getEnabledConnections(project),
                newEnabledAuthConnections = "",
                attempts = 1,
                result = Result.Failed
            )
        }
    }

    companion object {
        val LOG = getLogger<GettingStartedOnStartup>()
    }
}
