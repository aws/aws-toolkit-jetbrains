// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams

interface ModuleDependencyProvider {
    companion object {
        val EP_NAME = ExtensionPointName<ModuleDependencyProvider>("software.aws.toolkits.jetbrains.moduleDependencyProvider")
    }

    fun isApplicable(module: Module): Boolean
    fun createParams(module: Module): DidChangeDependencyPathsParams
}
