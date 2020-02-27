// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.Resource

object AwsExplorerService {
    fun refreshAwsTree(project: Project, resource: Resource<*>? = null) {
        val cache = AwsResourceCache.getInstance(project)
        if (resource == null) {
            cache.clear()
        } else {
            cache.clear(resource)
        }
        runInEdt {
            // redraw explorer
            ExplorerToolWindow.getInstance(project).invalidateTree()
        }
    }
}
