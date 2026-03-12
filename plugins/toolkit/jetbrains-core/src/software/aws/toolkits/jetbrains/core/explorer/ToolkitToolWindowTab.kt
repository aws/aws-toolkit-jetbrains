// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.awt.Component

interface ToolkitToolWindowTab {
    val tabId: String
    fun createContent(project: Project): Component
    fun enabled(): Boolean = true

    companion object {
        val EP_NAME = ExtensionPointName<ToolkitToolWindowTab>("aws.toolkit.toolWindowTab")
    }
}
