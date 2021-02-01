// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeTable
import software.aws.toolkits.resources.message

class ViewObjectVersionAction(private val treeTable: S3TreeTable) : SingleS3ObjectAction(message("s3.version.history.view"), AllIcons.Actions.ShowAsTree) {
    override fun performAction(dataContext: DataContext, node: S3TreeNode) {
        if (node is S3TreeObjectNode) {
            node.showHistory = true
            treeTable.refresh()
        }
    }

    override fun enabled(node: S3TreeNode): Boolean = node::class == S3TreeObjectNode::class
}
