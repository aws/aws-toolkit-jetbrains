// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.ObjectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError

class RenameObjectAction(treeTable: S3TreeTable, bucket: S3VirtualBucket) : AnActionButton("Rename Object", null, AllIcons.Actions.Refresh), TelemetryNamespace {
    var treeTable: S3TreeTable = treeTable
    val bucket: S3VirtualBucket = bucket
    @Suppress("unused")
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val row = treeTable.selectedRow
        val key = treeTable.getValueAt(row, 0).toString()
        println(key)
        val bucketName = bucket.key.bucket

        val response = Messages.showInputDialog(project,
            "Rename this object",
            "Rename Object",
            Messages.getWarningIcon(),
            null,
            object : InputValidator {
                override fun checkInput(inputString: String?): Boolean = true

                override fun canClose(inputString: String?): Boolean = checkInput(inputString)
            }
        )
        if (response != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val request = CopyObjectRequest.builder()
                        .copySource("$bucketName/$key").bucket(bucketName).key(response).build()
                    client.copyObject(request)
                    val deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(key).build()
                    client.deleteObject(deleteObjectRequest)
                    TelemetryService.getInstance().record(e.project, "s3") {
                        datum("renameobject") {
                            count()
                        }
                    }
//                    runInEdt {
//                        val path = treeTable.tree.getPathForRow(row)
//                        val node = path.lastPathComponent as DefaultMutableTreeNode
//                        val obj = node.userObject
//                        (obj as S3KeyNode).presentation.presentableText = response
//                        treeTable.tree.scrollPathToVisible(path)
//                        treeTable.invalidate()
//                        treeTable.repaint()
                    treeTable.refresh(bucket)
//                    }

//                        val mod= (treeTable as TreeTableModelWithColumns).delegate
//                        mod.setValueAt(response, row, 0)
//                        println(mod.isCellEditable(row, 0) )
//                         treeTable.setModel(treeTable.model)
//                        treeTable.setModel(mod)


//                        println(((node as DefaultMutableTreeNode).parent).children().toString())

//                    treeTable.tree.firePropertyChange()
//
//                    treeTable.model.setValueAt(treeTable.model.getValueAt(row, 1), row, 2)
//
//                    val mod = treeTable.model
//                    treeTable.setModel(mod)
//                    treeTable.invalidate()
//                    treeTable.repaint()
//                    treeTable.refresh(bucket)
                } catch (e: Exception) {
                    e.notifyError("Rename Object Failed")
                }
            }
        }
    }

    override fun isEnabled(): Boolean = !(treeTable.isEmpty || (treeTable.selectedRow < 0))

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun renameObjectAction(e: AnActionEvent, response: String) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val key = treeTable.getValueAt(treeTable.selectedRow, 0).toString()
        val request = CopyObjectRequest.builder()
            .copySource("${bucket.key.bucket}/$key").bucket(bucket.key.bucket).key(response).build()
        client.copyObject(request)
        val deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucket.key.bucket).key(key).build()
        client.deleteObject(deleteObjectRequest)
    }
}