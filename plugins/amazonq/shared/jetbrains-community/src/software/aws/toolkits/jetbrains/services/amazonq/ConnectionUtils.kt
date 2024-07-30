// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState

/**
 * Note: if a connection doesn't have all required scopes for Q, we determine it's NOT_AUTHENTICATED
 */
fun isQConnected(project: Project): Boolean {
    val manager = ToolkitConnectionManager.getInstance(project)
    val qState = manager.connectionStateForFeature(QConnection.getInstance())
    val cwState = manager.connectionStateForFeature(CodeWhispererConnection.getInstance())
    return qState != BearerTokenAuthState.NOT_AUTHENTICATED && cwState != BearerTokenAuthState.NOT_AUTHENTICATED
}

fun isQExpired(project: Project): Boolean {
    val manager = ToolkitConnectionManager.getInstance(project)
    val qState = manager.connectionStateForFeature(QConnection.getInstance())
    val cwState = manager.connectionStateForFeature(CodeWhispererConnection.getInstance())
    return qState == BearerTokenAuthState.NEEDS_REFRESH || cwState == BearerTokenAuthState.NEEDS_REFRESH
}
