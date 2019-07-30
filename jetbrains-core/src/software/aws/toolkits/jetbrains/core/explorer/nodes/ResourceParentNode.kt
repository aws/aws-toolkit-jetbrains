// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.nodes

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project

interface ResourceParentNode {
    val nodeProject: Project

    fun isAlwaysShowPlus(): Boolean = true

    fun getChildren(): List<AwsExplorerNode<*>> = try {
        val children = getChildrenInternal()
        if (children.isEmpty()) {
            listOf(emptyChildrenNode())
        } else {
            children
        }
    } catch (e: ExecutionException) {
        listOf(AwsExplorerErrorNode(nodeProject, e.cause ?: e))
    } catch (e: Exception) {
        listOf(AwsExplorerErrorNode(nodeProject, e))
    }

    fun emptyChildrenNode(): AwsExplorerEmptyNode = AwsExplorerEmptyNode(nodeProject)

    fun getChildrenInternal(): List<AwsExplorerNode<*>>
}