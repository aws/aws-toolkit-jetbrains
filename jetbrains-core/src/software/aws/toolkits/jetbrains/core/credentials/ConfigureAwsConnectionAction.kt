// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.resources.message

class ConfigureAwsConnectionAction(private val withRegions: Boolean = true, private val withCredentials: Boolean = true) :
    DumbAwareAction(message("configure.toolkit")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(PlatformDataKeys.PROJECT)
        ConnectionSettingsMenuBuilder.projectConnectionSettingsMenu(
            project,
            e.dataContext,
            withRegions,
            withCredentials
        ).showCenteredInCurrentWindow(project)
    }
}
