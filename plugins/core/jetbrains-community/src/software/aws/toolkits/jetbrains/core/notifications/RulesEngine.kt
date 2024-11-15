// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.ActiveConnection
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.ActiveConnectionType
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.checkBearerConnectionValidity
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.checkIamProfileByCredentialType
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend

object RulesEngine {

    fun displayNotification(notification: NotificationData, project: Project): Boolean {
        // If no conditions provided, show display the notification to everyone
        val shouldShow = notification.condition?.let { matchesAllRules(it, project) } ?: true
        return shouldShow
    }

    fun matchesAllRules(notificationConditions: NotificationDisplayCondition, project: Project): Boolean {
        val sysDetails = getCurrentSystemAndConnectionDetails()
        // If any of these conditions are null, we assume the condition to be true
        val compute = notificationConditions.compute?.let { matchesCompute(it, sysDetails.computeType, sysDetails.computeArchitecture) } ?: true
        val os = notificationConditions.os?.let { matchesOs(it, sysDetails.osType, sysDetails.osVersion) } ?: true
        val ide = notificationConditions.ide?.let { matchesIde(it, sysDetails.ideType, sysDetails.ideVersion) } ?: true
        val extension = matchesExtension(notificationConditions.extension, sysDetails.pluginVersions)
        val authx = matchesAuth(notificationConditions.authx, project)
        return compute && os && ide && extension && authx
    }

    private fun matchesCompute(notificationCompute: ComputeType, actualCompute: String, actualArchitecture: String): Boolean {
        val type = notificationCompute.type?.let { evaluateNotificationExpression(it, actualCompute) } ?: true
        val architecture = notificationCompute.architecture?.let { evaluateNotificationExpression(it, actualArchitecture) } ?: return true
        return type && architecture
    }

    private fun matchesOs(notificationOs: SystemType, actualOs: String, actualOsVersion: String): Boolean {
        val os = notificationOs.type?.let { evaluateNotificationExpression(it, actualOs) } ?: true
        val osVersion = notificationOs.version?.let { evaluateNotificationExpression(it, actualOsVersion) } ?: true
        return os && osVersion
    }

    private fun matchesIde(notificationIde: SystemType, actualIde: String, actualIdeVersion: String): Boolean {
        val ide = notificationIde.type?.let { evaluateNotificationExpression(it, actualIde) } ?: true
        val ideVersion = notificationIde.version?.let { evaluateNotificationExpression(it, actualIdeVersion) } ?: true
        return ide && ideVersion
    }

    private fun matchesExtension(notificationExtension: List<ExtensionType>?, actualPluginVersions: Map<String, String>): Boolean {
        if (notificationExtension.isNullOrEmpty()) return true
        val extensionsToBeChecked = notificationExtension.map { it.id }
        val pluginVersions = actualPluginVersions.filterKeys { extensionsToBeChecked.contains(it) }
        return notificationExtension.all { extension ->
            val actualVersion = pluginVersions[extension.id]
            if (actualVersion == null) {
                true
            } else {
                extension.version?.let { evaluateNotificationExpression(it, actualVersion) } ?: true
            }
        }
    }

    private fun matchesAuth(notificationAuth: List<AuthxType>?, project: Project): Boolean {
        if (notificationAuth.isNullOrEmpty()) return true
        return notificationAuth.all { feature ->
            val actualConnection = when (feature.feature) {
                "q" -> getConnectionDetailsForFeature(project, BearerTokenFeatureSet.Q)
                "codeCatalyst" -> getConnectionDetailsForFeature(project, BearerTokenFeatureSet.CODECATALYST)
                "toolkit" -> getConnectionDetailsForToolkit(project)
                else -> return true
            }

            if (actualConnection == null) {
                false
            } else {
                val authType = feature.type?.let { evaluateNotificationExpression(it, actualConnection.connectionType) } ?: true
                val authRegion = feature.region?.let { evaluateNotificationExpression(it, actualConnection.region) } ?: true
                val connectionState = feature.connectionState?.let { evaluateNotificationExpression(it, actualConnection.connectionState) } ?: true
                // TODO: Add condition for matching scopes
                authType && authRegion && connectionState
            }
        }
    }

    private fun evaluateNotificationExpression(notificationExpression: NotificationExpression, value: String): Boolean = when (notificationExpression) {
        is NotificationOperation -> performNotifOp(notificationExpression, value)
        is NotCondition -> performNotOp(notificationExpression, value)
        is OrCondition -> performOrOp(notificationExpression, value)
        is AndCondition -> performAndOp(notificationExpression, value)
        else -> true
    }

    private fun performNotifOp(notificationOperation: NotificationOperation, actualValue: String): Boolean = when (notificationOperation) {
        is ComparisonCondition -> notificationOperation.expectedValue == actualValue
        is NotEqualsCondition -> notificationOperation.expectedValue != actualValue
        is GreaterThanCondition -> actualValue > notificationOperation.expectedValue
        is LessThanCondition -> actualValue < notificationOperation.expectedValue
        is GreaterThanOrEqualsCondition -> actualValue >= notificationOperation.expectedValue
        is LessThanOrEqualsCondition -> actualValue <= notificationOperation.expectedValue
        is InCondition -> notificationOperation.expectedValueList.contains(actualValue)
        is NotInCondition -> !notificationOperation.expectedValueList.contains(actualValue)
        else -> true
    }

    private fun performNotOp(notificationOperation: NotCondition, actualValue: String): Boolean =
        !evaluateNotificationExpression(notificationOperation.expectedValue, actualValue)

    private fun performOrOp(notificationOperation: OrCondition, actualValue: String): Boolean =
        notificationOperation.expectedValueList.any { evaluateNotificationExpression(it, actualValue) }

    private fun performAndOp(notificationOperation: AndCondition, actualValue: String): Boolean =
        notificationOperation.expectedValueList.all { evaluateNotificationExpression(it, actualValue) }
}

fun getCurrentSystemAndConnectionDetails(): SystemDetails {
    val computeType: String = if (isRunningOnRemoteBackend()) "Remote" else "Local"
    val computeArchitecture: String = SystemInfo.OS_ARCH

    val osType: String = SystemInfo.OS_NAME
    val osVersion: String = SystemInfo.OS_VERSION

    val ideInfo = ApplicationInfo.getInstance()
    val ideType: String = ideInfo.build.productCode
    val ideVersion = ideInfo.shortVersion

    val pluginVersionMap = createPluginVersionMap()

    return SystemDetails(computeType, computeArchitecture, osType, osVersion, ideType, ideVersion, pluginVersionMap)
}

data class AuthDetails(
    val qConnection: ActiveConnection,
    val codeCatalystConnection: ActiveConnection,
    val explorerConnection: ActiveConnection,
)

data class FeatureAuthDetails(
    val connectionType: String,
    val region: String,
    val connectionState: String,
)

data class SystemDetails(
    val computeType: String,
    val computeArchitecture: String,
    val osType: String,
    val osVersion: String,
    val ideType: String,
    val ideVersion: String,
    val pluginVersions: Map<String, String>,
)

fun createPluginVersionMap(): Map<String, String> {
    val pluginVersionMap = mutableMapOf<String, String>()
    val pluginIds = listOf(
        "amazon.q",
        "aws.toolkit.core",
        "aws.toolkit"
    )
    pluginIds.forEach { pluginId ->
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
        val pluginVersion = plugin?.version
        if (pluginVersion != null) {
            pluginVersionMap[pluginId] = pluginVersion
        }
    }
    return pluginVersionMap
}

private fun getConnectionDetailsForToolkit(project: Project): FeatureAuthDetails? {
    val connection = checkIamProfileByCredentialType(project)
    if (connection.activeConnectionIam == null) return null
    val authType = when (connection.connectionType) {
        ActiveConnectionType.IAM_IDC -> "Idc"
        ActiveConnectionType.IAM -> "Iam"
        else -> "Unknown"
    }
    val authRegion = connection.activeConnectionIam?.defaultRegionId ?: "Unknown"

    val connectionState = when (connection) {
        is ActiveConnection.NotConnected -> "NotConnected"
        is ActiveConnection.ValidIam -> "Connected"
        is ActiveConnection.ExpiredIam -> "Expired"
        else -> "Unknown"
    }
    return FeatureAuthDetails(
        authType,
        authRegion,
        connectionState
    )
}

private fun getConnectionDetailsForFeature(project: Project, featureId: BearerTokenFeatureSet): FeatureAuthDetails? {
    val connection = checkBearerConnectionValidity(project, featureId)
    if (connection.activeConnectionBearer == null) return null
    val authType = when (connection.connectionType) {
        ActiveConnectionType.BUILDER_ID -> "BuilderId"
        ActiveConnectionType.IAM_IDC -> "Idc"
        else -> "Unknown"
    }
    val authRegion = connection.activeConnectionBearer?.region ?: "Unknown"

    val connectionState = when (connection) {
        is ActiveConnection.NotConnected -> "NotConnected"
        is ActiveConnection.ValidBearer -> "Connected"
        is ActiveConnection.ExpiredBearer -> "Expired"
        else -> "Unknown"
    }
    return FeatureAuthDetails(
        authType,
        authRegion,
        connectionState
    )
}
