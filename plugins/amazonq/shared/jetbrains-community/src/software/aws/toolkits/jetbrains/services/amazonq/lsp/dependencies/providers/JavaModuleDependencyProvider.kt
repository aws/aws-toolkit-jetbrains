// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.providers

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

internal class JavaModuleDependencyProvider : ModuleDependencyProvider {
    override fun isApplicable(module: Module): Boolean =
        ModuleRootManager.getInstance(module).sdk?.sdkType is JavaSdkType

    override fun createParams(module: Module): DidChangeDependencyPathsParams {
        val dependencies = mutableListOf<String>()

        ModuleRootManager.getInstance(module).orderEntries().forEachLibrary { library ->
            library.getFiles(OrderRootType.CLASSES).forEach { file ->
                dependencies.add(file.path.removeSuffix("!/"))
            }
            true
        }

        return DidChangeDependencyPathsParams(
            moduleName = getWorkspaceFolderPath(module),
            programmingLanguage = "java",
            paths = dependencies,
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
    }
}
