// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeTable
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

class NewFolderAction(
    private val project: Project,
    private val treeTable: S3TreeTable
) : SingleS3ObjectAction(message("s3.new.folder"), AllIcons.Actions.NewFolder), CoroutineScope by ApplicationThreadPoolScope("NewFolderAction") {
    override fun performAction(dataContext: DataContext, node: S3TreeNode) {
        Messages.showInputDialog(project, message("s3.new.folder.name"), message("s3.new.folder"), null)?.let { key ->
            launch {
                try {
                    treeTable.bucket.newFolder(node.directoryPath() + key)
                    treeTable.invalidateLevel(node)
                    treeTable.refresh()
                } catch (e: Exception) {
                    e.notifyError()
                }
            }
        }
    }
}
