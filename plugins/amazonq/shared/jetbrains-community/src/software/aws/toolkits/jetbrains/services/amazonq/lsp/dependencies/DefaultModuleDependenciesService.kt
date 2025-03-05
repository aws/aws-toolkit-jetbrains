// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.SyncModuleDependenciesParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider.Companion.EP_NAME
import java.util.concurrent.CompletableFuture

class DefaultModuleDependenciesService(
    private val project: Project,
    serverInstance: Disposable,
) : ModuleDependenciesService,
    ModuleRootListener {

    init {
        project.messageBus.connect(serverInstance).subscribe(
            ModuleRootListener.TOPIC,
            this
        )
        // project initiation with initial list of dependencies
        syncAllModules()
    }

    override fun rootsChanged(event: ModuleRootEvent) {
        if (event.isCausedByFileTypesChange) return
        // call on change with updated dependencies
        syncAllModules()
    }

    override fun syncModuleDependencies(params: SyncModuleDependenciesParams): CompletableFuture<Unit> =
        CompletableFuture<Unit>().also { completableFuture ->
            AmazonQLspService.executeIfRunning(project) { languageServer ->
                languageServer.syncModuleDependencies(params)
                completableFuture.complete(null)
            } ?: completableFuture.completeExceptionally(IllegalStateException("LSP Server not running"))
        }

    private fun syncAllModules() {
        ModuleManager.getInstance(project).modules.forEach { module ->
            EP_NAME.forEachExtensionSafe {
                if (it.isApplicable(module)) {
                    syncModuleDependencies(it.createParams(module))
                    return@forEachExtensionSafe
                }
            }
        }
    }

    private fun getSourceRoots(module: Module): List<String> {
        val sourceRoots = mutableListOf<String>()

        // Get all source roots from content entries
        ModuleRootManager.getInstance(module).contentEntries
            .flatMap { contentEntry ->
                contentEntry.sourceFolders
                    .filter { !it.isTestSource }
                    .mapNotNull { it.file?.path }
            }
            .forEach { path -> sourceRoots.add(path) }

        return sourceRoots
    }

    private fun createJavaParams(module: Module): SyncModuleDependenciesParams {
        val sourceRoots = getSourceRoots(module)
        val dependencies = mutableListOf<String>()

        // Get library dependencies
        ModuleRootManager.getInstance(module).orderEntries().forEachLibrary { library ->
            library.getUrls(OrderRootType.CLASSES).forEach { url ->
                dependencies.add(VfsUtil.urlToPath(url))
            }
            true
        }

        return SyncModuleDependenciesParams(
            moduleName = module.name,
            programmingLanguage = "Java",
            files = sourceRoots,
            dirs = dependencies,
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
    }

    private fun createPythonParams(module: Module): SyncModuleDependenciesParams {
        val sourceRoots = getSourceRoots(module)
        val dependencies = mutableListOf<String>()

        // Get Python packages
        PythonSdkUtil.findPythonSdk(module)?.let { sdk ->
            val packageManager = PythonPackageManager.forSdk(module.project, sdk)
            packageManager.installedPackages.forEach { pkg ->
                dependencies.add(pkg.name)
            }
        }

        return SyncModuleDependenciesParams(
            moduleName = module.name,
            programmingLanguage = "Python",
            files = sourceRoots,
            dirs = dependencies,
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
    }
}
