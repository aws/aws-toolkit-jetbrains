// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree

class AwsExplorerFilterManager(private val project: Project) {
    private var currentFilter: AwsExplorerFilter? = null

    fun setFilter(filter: AwsExplorerFilter) {
        currentFilter = filter
        project.refreshAwsTree()
    }

    fun clearFilter() {
        (currentFilter as? Disposable)?.let { Disposer.dispose(it) }
        currentFilter = null
        project.refreshAwsTree()
    }

    fun currentFilter(): AwsExplorerFilter? = currentFilter

    companion object {
        fun getInstance(project: Project): AwsExplorerFilterManager = project.service()
    }
}
