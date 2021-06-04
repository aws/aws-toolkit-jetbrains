// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.apprunner.actions

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import software.amazon.awssdk.services.apprunner.model.ServiceSummary
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.apprunner.AppRunnerServiceNode
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.ApprunnerTelemetry
import java.awt.datatransfer.StringSelection

class CopyServiceUrlAction : SingleResourceNodeAction<AppRunnerServiceNode>(message("apprunner.action.copyServiceUrl")), DumbAware {
    override fun actionPerformed(selected: AppRunnerServiceNode, e: AnActionEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(selected.service.urlWithProtocol()))
        ApprunnerTelemetry.copyServiceUrl(selected.nodeProject)
    }
}

class OpenServiceUrlAction internal constructor(private val launcher: BrowserLauncher) :
    SingleResourceNodeAction<AppRunnerServiceNode>(message("apprunner.action.openServiceUrl")), DumbAware {
    constructor() : this(BrowserLauncher.instance)

    override fun actionPerformed(selected: AppRunnerServiceNode, e: AnActionEvent) {
        launcher.browse(selected.service.urlWithProtocol(), project = selected.nodeProject)
        ApprunnerTelemetry.openServiceUrl(selected.nodeProject)
    }
}

private fun ServiceSummary.urlWithProtocol() = "https://${serviceUrl()}"
