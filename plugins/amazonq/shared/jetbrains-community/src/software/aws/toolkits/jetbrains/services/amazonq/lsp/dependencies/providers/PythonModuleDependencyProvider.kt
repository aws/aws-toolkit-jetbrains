// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.providers

import com.intellij.openapi.module.Module
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

internal class PythonModuleDependencyProvider : ModuleDependencyProvider {
    override fun isApplicable(module: Module): Boolean =
        PythonSdkUtil.findPythonSdk(module) != null

    override fun createParams(module: Module): DidChangeDependencyPathsParams {
        val dependencies = mutableListOf<String>()

        PythonSdkUtil.findPythonSdk(module)?.let { sdk ->
            PythonSdkUtil.getSitePackagesDirectory(sdk)?.let { sitePackagesDir ->
                val packageManager = PythonPackageManager.forSdk(module.project, sdk)
                packageManager.installedPackages.forEach { pkg ->
                    val packageDir = sitePackagesDir.findChild(pkg.name)
                    if (packageDir != null) {
                        dependencies.add(packageDir.path)
                    }
                }
            }
        }

        return DidChangeDependencyPathsParams(
            moduleName = getWorkspaceFolderPath(module),
            programmingLanguage = "python",
            paths = dependencies,
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
    }
}
