// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.logoutFromSsoConnection
import software.aws.toolkits.resources.AwsCoreBundle

class SsoLogoutAction(private val value: AwsBearerTokenConnection) : DumbAwareAction(AwsCoreBundle.message("credentials.individual_identity.signout")) {
    override fun actionPerformed(e: AnActionEvent) {
        logoutFromSsoConnection(e.project, value)
        ApplicationManager.getApplication().messageBus.syncPublisher(
            ToolkitConnectionManagerListener.TOPIC
        ).activeConnectionChanged(null)
    }
}
