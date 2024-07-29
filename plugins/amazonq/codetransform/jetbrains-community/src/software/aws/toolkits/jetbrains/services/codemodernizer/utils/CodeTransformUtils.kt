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
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.resources.message

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
    else -> TransformationLanguage.UNKNOWN_TO_SDK_VERSION
}
