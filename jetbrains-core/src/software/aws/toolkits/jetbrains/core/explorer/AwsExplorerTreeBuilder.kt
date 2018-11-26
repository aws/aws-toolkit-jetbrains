// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.ide.util.treeView.AbstractTreeBuilder
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

class AwsExplorerTreeBuilder(tree: JTree, treeModel: DefaultTreeModel, project: Project) :
        AbstractTreeBuilder(tree, treeModel, AwsExplorerTreeStructure(project), null, false) {
    init {
        initRootNode()
    }

    override fun runBackgroundLoading(runnable: Runnable) {
        // TODO: Hack so we don't run in read actions
        runnable.run()
    }

    override fun isSmartExpand() = false

    override fun isAlwaysShowPlus(descriptor: NodeDescriptor<*>?) = descriptor is AwsExplorerServiceRootNode || descriptor is AwsNodeAlwaysExpandable
}