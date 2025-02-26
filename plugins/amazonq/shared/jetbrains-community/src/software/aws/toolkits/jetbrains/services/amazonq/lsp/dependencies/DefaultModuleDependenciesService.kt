// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.SyncModuleDependenciesParams

class DefaultModuleDependenciesService(private val project: Project) : ModuleDependenciesService {

//    init {
        // TODO: add moduleRootListener to make function call that eventually sends message w/ params
//    }

    override fun syncModuleDependencies(params: SyncModuleDependenciesParams) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            languageServer.syncModuleDependencies(params)
        }
    }

//    fun createSyncModuleDependenciesParams() :  SyncModuleDependenciesParams {
//        return SyncModuleDependenciesParams()
//    }
}
