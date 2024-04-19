// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.FeatureWithPinnedConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded

fun Project.activeConnection(feat: FeatureWithPinnedConnection = QConnection.getInstance()): AwsBearerTokenConnection? {
    return ToolkitConnectionManager.getInstance(this).activeConnectionForFeature(feat) as? AwsBearerTokenConnection?
}

fun AwsBearerTokenConnection.reconnect() {
    if (this !is ManagedBearerSsoConnection) {
        return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
        // TODO: is it ok to pass project=null?
        reauthConnectionIfNeeded(null, this)
    }
}
