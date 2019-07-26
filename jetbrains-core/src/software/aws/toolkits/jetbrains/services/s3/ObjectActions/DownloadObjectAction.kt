// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.ObjectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.AnActionButton
import com.intellij.ui.treeStructure.treetable.TreeTable
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.aws.toolkits.jetbrains.core.AwsClientManager
import java.nio.file.Paths
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError

class DownloadObjectAction(treeTable: TreeTable, bucket: S3VirtualBucket, fileChooser: FileChooserFactory) : AnActionButton("Download Object", null, AllIcons.Actions.Download), TelemetryNamespace {
    val treeTable: TreeTable = treeTable
    val bucket: S3VirtualBucket = bucket
    val fileChooser: FileChooserFactory = fileChooser

    constructor(treeTable: TreeTable, bucket: S3VirtualBucket) : this(treeTable, bucket, FileChooserFactory.getInstance())

    @Suppress("unused")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val row = treeTable.selectedRow
        var key = treeTable.getValueAt(row, 0).toString()
        key = if (key.contains("/")) key.substringAfter("/") else key
        val bucketName = bucket.key.bucket

        val descriptor = FileSaverDescriptor("Download Object", "Download",
            "jpg,png,txt, pdf")

        val saveFileDialog = fileChooser.createSaveFileDialog(descriptor, project)
        val baseDir = VfsUtil.getUserHomeDir()
        val fileWrapper = saveFileDialog.save(baseDir, key)

        if (fileWrapper != null) {
            val path = fileWrapper.file.path
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val request = GetObjectRequest.builder().bucket(bucketName).key(key).build()
                    client.getObject(request, Paths.get(path))
                    TelemetryService.getInstance().record(e.project, "s3") {
                        datum("downloadobject") {
                            count()
                        }
                    }
                } catch (e: Exception) {
                    notifyError("Download failed")
                }
            }
        }
    }

    override fun isEnabled(): Boolean = !(treeTable.isEmpty || (treeTable.selectedRow < 0))

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun downloadObjectAction(e: AnActionEvent, saveDialog: FileSaverDialog) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val row = treeTable.selectedRow
        val key = treeTable.getValueAt(row, 0).toString()
        val baseDir = VfsUtil.getUserHomeDir()
        val fileWrapper = saveDialog.save(baseDir, key)

        val path = fileWrapper!!.file.path
        val request = GetObjectRequest.builder().bucket(bucket.key.bucket).key(key).build()
        client.getObject(request, Paths.get(path))

    }
}