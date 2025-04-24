// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider.Companion.EP_NAME
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams
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

    override fun didChangeDependencyPaths(params: DidChangeDependencyPathsParams): CompletableFuture<Unit> =
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            languageServer.didChangeDependencyPaths(params)
        }?.toCompletableFuture() ?: CompletableFuture.failedFuture(IllegalStateException("LSP Server not running"))

    private fun syncAllModules() {
        ModuleManager.getInstance(project).modules.forEach { module ->
            EP_NAME.forEachExtensionSafe {
                if (it.isApplicable(module)) {
                    didChangeDependencyPaths(it.createParams(module))
                    return@forEachExtensionSafe
                }
            }
        }
    }
}
