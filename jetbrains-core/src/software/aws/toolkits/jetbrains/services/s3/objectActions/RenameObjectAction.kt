// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnActionButton
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory
import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3KeyNode
import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError
import javax.swing.tree.DefaultMutableTreeNode

class RenameObjectAction(private var treeTable: S3TreeTable, val bucket: S3VirtualBucket) :
    AnActionButton("Rename Object", null, AllIcons.Actions.Refresh), TelemetryNamespace {

    @Suppress("unused")
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val row = treeTable.selectedRow
        val path = treeTable.tree.getPathForRow(row)
        val node = (path.lastPathComponent as DefaultMutableTreeNode).userObject as S3KeyNode
        val file = node.virtualFile

        val response = Messages.showInputDialog(project,
            "Rename Object :${file.name} to",
            "Rename Object",
            null,
            file.name,
            object : InputValidator {
                override fun checkInput(inputString: String?): Boolean = true

                override fun canClose(inputString: String?): Boolean = checkInput(inputString)
            }
        )
        if (response != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    renameObjectAction(response, file, client)
                    TelemetryService.getInstance().record(e.project, "s3") {
                        datum("renameobject") {
                            count()
                        }
                    }
                    treeTable.refresh()
                } catch (e: Exception) {
                    e.notifyError("Rename Object Failed")
                }
            }
        }
    }

    override fun isEnabled(): Boolean = !(treeTable.isEmpty || (treeTable.selectedRow < 0) ||
            (treeTable.getValueAt(treeTable.selectedRow, 1) == "") || (treeTable.selectedRows.size > 1))

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun renameObjectAction(response: String, file: VirtualFile, client: S3Client) {
        val bucketName = bucket.getVirtualBucketName()
        var copySource: String
        var copyDestination: String
        if (file.parent is S3VirtualDirectory) {
            copySource = "${file.parent.name}/${file.name}"
            copyDestination = "${file.parent.name}/$response"
        } else {
            copySource = file.name
            copyDestination = response
        }
        var copyObjectRequest: CopyObjectRequest =
            when (file.name.contains("/")) {
                true -> CopyObjectRequest.builder()
                    .copySource("$bucketName/$copySource")
                    .bucket(bucketName)
                    .key("$copyDestination")
                    .build()

                false -> CopyObjectRequest.builder()
                    .copySource("$bucketName/$copySource")
                    .bucket(bucketName)
                    .key(copyDestination)
                    .build()
            }
        client.copyObject(copyObjectRequest)

        val deleteObjectRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(copySource)
            .build()
        client.deleteObject(deleteObjectRequest)
    }
}