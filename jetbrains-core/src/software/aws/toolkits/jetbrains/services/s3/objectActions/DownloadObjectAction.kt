// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.AnActionButton
import com.intellij.ui.treeStructure.treetable.TreeTable
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3KeyNode
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError
import javax.swing.tree.DefaultMutableTreeNode

class DownloadObjectAction(
    private val treeTable: TreeTable,
    val bucket: S3VirtualBucket,
    private val fileChooser: FileChooserFactory
) : AnActionButton("Download Object", null, AllIcons.Actions.Download), TelemetryNamespace {

    constructor(treeTable: TreeTable, bucket: S3VirtualBucket) : this(treeTable, bucket, FileChooserFactory.getInstance())

    @Suppress("unused")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val descriptor = FileSaverDescriptor(
            "Download Object", "Download"
        )
        val saveFileDialog = fileChooser.createSaveFileDialog(descriptor, project)
        val baseDir = VfsUtil.getUserHomeDir()
        val rows = treeTable.selectedRows

        for (row in rows) {
            val path = treeTable.tree.getPathForRow(row)
            val node = (path.lastPathComponent as DefaultMutableTreeNode).userObject as S3KeyNode
            val fileSelected = node.virtualFile
            val fileWrapper = saveFileDialog.save(baseDir, fileSelected.name)
            if (fileWrapper != null) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        downloadObjectAction(project, client, fileSelected, fileWrapper)
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
    }

    override fun isEnabled(): Boolean = !(treeTable.isEmpty || (treeTable.selectedRow < 0) ||
            (treeTable.getValueAt(treeTable.selectedRow, 1) == ""))

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun downloadObjectAction(project: Project, client: S3Client, file: VirtualFile, fileWrapper: VirtualFileWrapper) {
        val bucketName = bucket.getVirtualBucketName()
        val request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(file.name)
            .build()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading \"${file.name}\"", true) {
            override fun run(indicator: ProgressIndicator) {
                val fileOutputStream = fileWrapper.file.outputStream()
                val progressStream = ProgressOutputStream(
                    fileOutputStream,
                    file.length,
                    indicator
                )
                client.getObject(request, ResponseTransformer.toOutputStream(progressStream))
            }
        })
    }
}