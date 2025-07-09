// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider.Companion.EP_NAME
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

class DefaultModuleDependenciesService(
    private val project: Project,
    private val cs: CoroutineScope,
) : ModuleDependenciesService,
    ModuleRootListener,
    Disposable {
    init {
        project.messageBus.connect(this).subscribe(
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

    override fun didChangeDependencyPaths(params: DidChangeDependencyPathsParams) {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
                languageServer.didChangeDependencyPaths(params)
            }
        }
    }

    private fun syncAllModules() {
        val paramsMap = mutableMapOf<Pair<String, String>, DidChangeDependencyPathsParams>()

        ModuleManager.getInstance(project).modules.forEach { module ->
            EP_NAME.forEachExtensionSafe {
                if (it.isApplicable(module)) {
                    val params = it.createParams(module)
                    val key = params.moduleName to params.runtimeLanguage

                    paramsMap.merge(key, params) { existing, new ->
                        DidChangeDependencyPathsParams(
                            moduleName = existing.moduleName,
                            runtimeLanguage = existing.runtimeLanguage,
                            paths = (existing.paths + new.paths).distinct(),
                            includePatterns = (existing.includePatterns + new.includePatterns).distinct(),
                            excludePatterns = (existing.excludePatterns + new.excludePatterns).distinct()
                        )
                    }
                    return@forEachExtensionSafe
                }
            }
        }

        paramsMap.values.forEach { didChangeDependencyPaths(it) }
    }

    override fun dispose() {
    }
}
