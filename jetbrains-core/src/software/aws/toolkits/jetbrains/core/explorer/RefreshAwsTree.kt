// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.Resource

/*
 * Redraw the AWS Tree without affecting the state of the resources
 */
fun Project.redrawAwsTree() {
    runInEdt {
        // redraw explorer
        ExplorerToolWindow.getInstance(this).invalidateTree()
    }
}

/*
 * Refresh the AWS Tree by removing stale resources then redrawing the tree
 *
 * @param resource The resource type to clear, or null for all resource types (empty the cache)
 */
fun Project.refreshAwsTree(resource: Resource<*>? = null) {
    val cache = AwsResourceCache.getInstance(this)
    if (resource == null) {
        cache.clear()
    } else {
        cache.clear(resource)
    }
    redrawAwsTree()
}
