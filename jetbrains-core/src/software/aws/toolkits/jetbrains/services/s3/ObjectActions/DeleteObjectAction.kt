// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.ObjectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

class DeleteObjectAction(treeTable: S3TreeTable, bucket: S3VirtualBucket) : AnActionButton("Delete Object", null, AllIcons.Actions.Cancel), TelemetryNamespace {
    var treeTable: S3TreeTable = treeTable
    val bucket: S3VirtualBucket = bucket

    @Suppress("unused")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val row = treeTable.selectedRow
        var key = treeTable.getValueAt(row, 0).toString()
        val bucketName = bucket.key.bucket

        val response = Messages.showOkCancelDialog(project,
            "Are you sure you want to delete this object?", message("delete_resource.title", "Object:", key),
            "Delete", "Cancel", Messages.getWarningIcon())
        if (response == 0) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(key).build()
                    client.deleteObject(deleteObjectRequest)
                    TelemetryService.getInstance().record(e.project, "s3") {
                        datum("deleteobject") {
                            count()
                        }
                    }
//                    runInEdt {
//                        val path = treeTable.tree.getPathForRow(row)
//                            val node = path.lastPathComponent
//                        treeTable.removeSelectedPath(path)
//                    }
                    treeTable.refresh(bucket)
                } catch (e: Exception) {
                    notifyInfo("Delete Successful")
                }
            }
        }
    }

    override fun isEnabled(): Boolean = !(treeTable.isEmpty || (treeTable.selectedRow < 0))

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun deleteObjectAction(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucket.key.bucket).key(treeTable.getValueAt(treeTable.selectedRow, 0).toString()).build()
        client.deleteObject(deleteObjectRequest)
    }
}