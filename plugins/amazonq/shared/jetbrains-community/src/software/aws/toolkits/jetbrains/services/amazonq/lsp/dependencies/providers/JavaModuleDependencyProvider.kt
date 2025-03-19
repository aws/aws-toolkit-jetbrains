// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.providers

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

internal class JavaModuleDependencyProvider : ModuleDependencyProvider {
    override fun isApplicable(module: Module): Boolean =
        ModuleRootManager.getInstance(module).sdk?.sdkType is JavaSdkType

    override fun createParams(module: Module): DidChangeDependencyPathsParams {
        val sourceRoots = getSourceRoots(module)
        val dependencies = mutableListOf<String>()

        ModuleRootManager.getInstance(module).orderEntries().forEachLibrary { library ->
            library.getUrls(OrderRootType.CLASSES).forEach { url ->
                dependencies.add(VfsUtil.urlToPath(url))
            }
            true
        }

        return DidChangeDependencyPathsParams(
            moduleName = module.name,
            programmingLanguage = "Java",
            files = sourceRoots,
            dirs = dependencies,
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
    }

    private fun getSourceRoots(module: Module): List<String> =
        ModuleRootManager.getInstance(module).contentEntries
            .flatMap { contentEntry ->
                contentEntry.sourceFolders
                    .filter { !it.isTestSource }
                    .mapNotNull { it.file?.path }
            }
}
