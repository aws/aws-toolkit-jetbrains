// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationLanguage
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.ActiveConnection
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.ActiveConnectionType
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.checkBearerConnectionValidity
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CredentialSourceId

val STATES_WHERE_PLAN_EXIST = setOf(
    TransformationStatus.PLANNED,
    TransformationStatus.TRANSFORMING,
    TransformationStatus.TRANSFORMED,
    TransformationStatus.PARTIALLY_COMPLETED,
    TransformationStatus.COMPLETED,
    TransformationStatus.PAUSED,
    TransformationStatus.RESUMED,
)

val STATES_AFTER_INITIAL_BUILD = setOf(
    TransformationStatus.PREPARED,
    TransformationStatus.PLANNING,
    *STATES_WHERE_PLAN_EXIST.toTypedArray()
)

val STATES_AFTER_STARTED = setOf(
    TransformationStatus.STARTED,
    TransformationStatus.PREPARING,
    *STATES_AFTER_INITIAL_BUILD.toTypedArray(),
)

fun refreshToken(project: Project) {
    val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
    val provider = (connection?.getConnectionSettings() as TokenConnectionSettings).tokenProvider.delegate as BearerTokenProvider
    provider.refresh()
}

fun getAuthType(project: Project): CredentialSourceId? {
    val connection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q)
    var authType: CredentialSourceId? = null
    if (connection.connectionType == ActiveConnectionType.IAM_IDC && connection is ActiveConnection.ValidBearer) {
        authType = CredentialSourceId.IamIdentityCenter
    } else if (connection.connectionType == ActiveConnectionType.BUILDER_ID && connection is ActiveConnection.ValidBearer) {
        authType = CredentialSourceId.AwsId
    }
    return authType
}

fun getQTokenProvider(project: Project) = (
    ToolkitConnectionManager
        .getInstance(project)
        .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
    )
    ?.getConnectionSettings()
    ?.tokenProvider
    ?.delegate as? BearerTokenProvider

fun openTroubleshootingGuideNotificationAction(targetUrl: String) = OpenBrowserAction(
    message("codemodernizer.notification.info.view_troubleshooting_guide"),
    url = targetUrl
)

fun String.toVirtualFile() = VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(this))

fun String.toTransformationLanguage() = when (this) {
    "JDK_1_8" -> TransformationLanguage.JAVA_8
    "JDK_11" -> TransformationLanguage.JAVA_11
    "JDK_17" -> TransformationLanguage.JAVA_17
    "JDK_21" -> TransformationLanguage.JAVA_21
    else -> TransformationLanguage.UNKNOWN_TO_SDK_VERSION
}

fun createJavaHomePrompt(jdkVersion: String): String {
    var javaHomePrompt = "Enter the path to $jdkVersion.\n\n"
    val os = System.getProperty("os.name").lowercase()
    if (os.contains("windows")) {
        javaHomePrompt += "To find the JDK path, run the following commands in a new terminal: `cd \"C:/Program Files/Java\"` and then `dir`. " +
            "If you see your JDK version, run `cd <version>` and then `cd` to show the path."
    } else if (os.contains("mac") || os.contains("darwin")) {
        val version = when (jdkVersion) {
            "JDK_1_8" -> "1.8"
            "JDK_11" -> "11"
            "JDK_17" -> "17"
            "JDK_21" -> "21"
            else -> "JAVA_VERSION" // shouldn't happen; we only support Java 8, 11, 17, and 21
        }
        javaHomePrompt += "To find the JDK path, run the following command in a new terminal: `/usr/libexec/java_home -v $version`"
    } else {
        javaHomePrompt += "To find the JDK path, run the following command in a new terminal: `update-java-alternatives --list`"
    }
    return javaHomePrompt
}
