// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

interface ModuleDependencyProvider {
    companion object {
        val EP_NAME = ExtensionPointName<ModuleDependencyProvider>("software.aws.toolkits.jetbrains.moduleDependencyProvider")
    }

    fun isApplicable(module: Module): Boolean
    fun createParams(module: Module): DidChangeDependencyPathsParams

    fun getWorkspaceFolderPath(module: Module): String {
        val contentRoots: Array<VirtualFile> = ModuleRootManager.getInstance(module).contentRoots
        return contentRoots.firstOrNull()?.path.orEmpty()
    }
}
