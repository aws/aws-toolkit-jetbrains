// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.SyncModuleDependenciesParams
import java.util.concurrent.CompletableFuture

interface ModuleDependenciesService {
    fun syncModuleDependencies(params: SyncModuleDependenciesParams): CompletableFuture<Unit>
}
