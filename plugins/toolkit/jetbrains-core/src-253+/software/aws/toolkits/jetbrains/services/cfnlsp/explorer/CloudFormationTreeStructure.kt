// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.project.Project

class CloudFormationTreeStructure(project: Project) : AbstractTreeStructureBase(project) {
    override fun getRootElement() = CloudFormationRootNode(myProject)
    override fun getProviders(): List<TreeStructureProvider>? = null
    override fun commit() {}
    override fun hasSomethingToCommit(): Boolean = false
}
