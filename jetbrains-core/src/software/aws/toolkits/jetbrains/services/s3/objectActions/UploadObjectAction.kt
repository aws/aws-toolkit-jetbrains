// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnActionButton
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory
import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3KeyNode
import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError
import javax.swing.tree.DefaultMutableTreeNode

class UploadObjectAction(
    val bucket: S3VirtualBucket,
    val treeTable: S3TreeTable,
    private val fileChooserFactory: FileChooserFactory
) : AnActionButton("Upload Object", null, AllIcons.Actions.Upload), TelemetryNamespace {

    constructor(bucket: S3VirtualBucket, treeTable: S3TreeTable) : this(
        bucket,
        treeTable,
        FileChooserFactory.getInstance()
    )

    @Suppress("unused")
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
            .withDescription("Upload object")

        val row = treeTable.selectedRow
        println(row)
        var nodeFile: VirtualFile? = null
        if (row > 0) {
            val path = treeTable.tree.getPathForRow(row)
            val node = (path.lastPathComponent as DefaultMutableTreeNode).userObject as S3KeyNode
            nodeFile = node.virtualFile
        }
        val chooserDialog = fileChooserFactory.createFileChooser(descriptor, project, null)
        val filesChosen = chooserDialog.choose(project, null)
        for (fileChosen in filesChosen) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    uploadObjectAction(client, project, fileChosen, nodeFile)
                    TelemetryService.getInstance().record(e.project, "s3") {
                        datum("uploadobject") {
                            count()
                        }
                    }
                    treeTable.refresh()
                } catch (e: Exception) {
                    notifyError("Upload failed")
                }
            }
        }
    }

    override fun isEnabled(): Boolean = treeTable.isEmpty || !(treeTable.selectedRows.size > 1)

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun uploadObjectAction(
        client: S3Client,
        project: Project,
        fileChosen: VirtualFile,
        nodeFile: VirtualFile?
    ) {
        val bucketName = bucket.getVirtualBucketName()
        var key: String
        if (nodeFile is S3VirtualDirectory) {
            key = "${nodeFile.name}/${fileChosen.name}"
        } else if (nodeFile?.parent is S3VirtualDirectory) {
            key = "${nodeFile.parent.name}/${fileChosen.name}"
        } else {
            key = fileChosen.name
        }
        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()

        val fileChosenSize = fileChosen.inputStream.readBytes().size

        ProgressManager.getInstance()
            .run(object : Task.Modal(project, "Uploading \"${fileChosen.name}\"", false) {
                override fun run(indicator: ProgressIndicator) {
                    val pStream = ProgressInputStream(fileChosen.inputStream, fileChosenSize, indicator)
                    client.putObject(request, RequestBody.fromInputStream(pStream, fileChosen.length))
                }
            })
    }
}