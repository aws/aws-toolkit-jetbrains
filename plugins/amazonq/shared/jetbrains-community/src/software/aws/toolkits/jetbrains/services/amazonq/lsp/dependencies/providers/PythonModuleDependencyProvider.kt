// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.providers

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

internal class PythonModuleDependencyProvider : ModuleDependencyProvider {
    override fun isApplicable(module: Module): Boolean =
        PythonSdkUtil.findPythonSdk(module) != null

    override fun createParams(module: Module): DidChangeDependencyPathsParams {
        val sourceRoots = getSourceRoots(module)
        val dependencies = mutableListOf<String>()

        PythonSdkUtil.findPythonSdk(module)?.let { sdk ->
            val packageManager = PythonPackageManager.forSdk(module.project, sdk)
            packageManager.installedPackages.forEach { pkg ->
                dependencies.add(pkg.name)
            }
        }

        return DidChangeDependencyPathsParams(
            moduleName = module.name,
            programmingLanguage = "Python",
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
