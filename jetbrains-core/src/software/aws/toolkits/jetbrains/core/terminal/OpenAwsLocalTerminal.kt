// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.terminal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.util.ExceptionUtil
import icons.TerminalIcons
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalView
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.credentials.shortName
import software.aws.toolkits.jetbrains.core.plugins.pluginIsInstalledAndEnabled
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.Result

class OpenAwsLocalTerminal : AnAction(message("aws.terminal.action"), message("aws.terminal.action.tooltip"), TerminalIcons.OpenTerminal_13x13) {

    override fun update(e: AnActionEvent) {
        if (!pluginIsInstalledAndEnabled("org.jetbrains.plugins.terminal")) {
            e.presentation.isEnabled = false
            e.presentation.isVisible = false
            return
        }
        e.presentation.isEnabled = e.project?.let { AwsConnectionManager.getInstance(it) }?.isValidConnectionSettings() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        when (val state = AwsConnectionManager.getInstance(project).connectionState) {
            is ConnectionState.ValidConnection -> {
                val connection = state.connection
                val credentials = try {
                    connection.credentials.resolveCredentials()
                } catch (e: Exception) {
                    LOG.error(e) { message("aws.terminal.exception.failed_to_resolve_credentials", ExceptionUtil.getThrowableText(e)) }
                    AwsTelemetry.openLocalTerminal(project, result = Result.Failed)
                    return
                }
                runInEdt {
                    val runner = AwsLocalTerminalRunner(project, connection.region, credentials)
                    TerminalView.getInstance(project).createNewSession(runner, TerminalTabState().apply { this.myTabName = connection.shortName })
                    AwsTelemetry.openLocalTerminal(project, result = Result.Succeeded)
                }
            }
            else -> {
                LOG.error { message("aws.terminal.exception.invalid_credentials", state.displayMessage) }
                AwsTelemetry.openLocalTerminal(project, result = Result.Failed)
            }
        }
    }

    private companion object {
        private val LOG = getLogger<OpenAwsLocalTerminal>()
    }
}
