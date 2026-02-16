// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class RefreshAllAction : AnAction(
    message("cloudformation.explorer.refresh_all"),
    null,
    AllIcons.Actions.Refresh,
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        StacksManager.getInstance(project).reload()
        val resourceLoader = ResourceLoader.getInstance(project)
        resourceLoader.getLoadedResourceTypes().forEach { resourceLoader.refreshResources(it) }
    }
}
