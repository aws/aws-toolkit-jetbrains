// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.terminal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runInEdt
import icons.TerminalIcons
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalView
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.shortName
import software.aws.toolkits.jetbrains.core.plugins.pluginIsInstalledAndEnabled
import software.aws.toolkits.resources.message

class StartAwsTerminal : AnAction(
    message("aws.terminal.action"),
    message("aws.terminal.action.tooltip"),
    TerminalIcons.OpenTerminal_13x13
) {

    override fun update(e: AnActionEvent) {
        if (!pluginIsInstalledAndEnabled("org.jetbrains.plugins.terminal")) {
            e.presentation.isEnabled = false
            e.presentation.isVisible = false
            return
        }
        e.presentation.isEnabled = validConnection(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        when (val connection = validConnection(e)) {
            is ConnectionSettings -> runInEdt {
                val runner = AwsTerminalRunner(project, connection)
                TerminalView.getInstance(project).createNewSession(runner, TerminalTabState().apply { this.myTabName = connection.shortName })
            }
            null -> LOG.error("Unable to start AWS Terminal - connection settings invalid")
        }
    }

    private companion object {
        private val LOG = getLogger<StartAwsTerminal>()
        private fun validConnection(e: AnActionEvent): ConnectionSettings? {
            val project = e.project ?: return null
            val connectionManager = AwsConnectionManager.getInstance(project)
            if (connectionManager.isValidConnectionSettings()) {
                return connectionManager.connectionSettings()
            }
            return null
        }
    }
}
