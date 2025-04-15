// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import org.eclipse.lsp4j.WorkspaceFolder
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.FileUriUtil.toUriString

object WorkspaceFolderUtil {
    fun createWorkspaceFolders(project: Project): List<WorkspaceFolder> =
        if (project.isDefault) {
            emptyList()
        } else {
            ModuleManager.getInstance(project).modules.mapNotNull { module ->
                ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.let { contentRoot ->
                    WorkspaceFolder().apply {
                        name = module.name
                        uri = toUriString(contentRoot)
                    }
                }
            }
        }
}
