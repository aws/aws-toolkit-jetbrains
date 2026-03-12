// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.explorer.ToolkitToolWindowTab
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.Component

internal class CloudFormationToolWindowTab : ToolkitToolWindowTab {
    override val tabId: String = message("cloudformation.explorer.tab.title")

    override fun createContent(project: Project): Component =
        CloudFormationToolWindow.getInstance(project)
}
