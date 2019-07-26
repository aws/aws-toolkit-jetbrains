// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.ObjectActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AnActionButton
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError

class UploadObjectAction(s3Bucket: S3VirtualBucket, treeTable: S3TreeTable, fileChooser: FileChooserFactory) : AnActionButton("Upload Object", null, AllIcons.Actions.Upload), TelemetryNamespace {
    val bucket: S3VirtualBucket = s3Bucket
    val fileChooser: FileChooserFactory = fileChooser
    val treeTable: S3TreeTable = treeTable
//    private val progressIndicator = ProgressIndicatorBase()

    constructor(bucket: S3VirtualBucket, treeTable: S3TreeTable) : this(bucket, treeTable, FileChooserFactory.getInstance())

    @Suppress("unused") // Used by ActionManager in plugin.xml

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val bucketName = bucket.key.bucket
        val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
            .withDescription("Upload object")
        val row = treeTable.selectedRow
        val key = if (row < 0) null else treeTable.getValueAt(row, 0).toString()

        val chooser = fileChooser.createFileChooser(descriptor, project, null)
        val fileChooser = chooser.choose(project, null)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                var request: PutObjectRequest
                when (key != null && key.endsWith("/")) {
                    true -> request = PutObjectRequest.builder().bucket(bucketName).key("$key${fileChooser[0].name}").build()
                    false -> request = PutObjectRequest.builder().bucket(bucketName).key(fileChooser[0].name).build()
                }

//                TelemetryService.getInstance().record(e.project, "s3") {
//                    datum("uploadobject") {
//                        count()

                val byteSize = fileChooser[0].inputStream.readBytes().size
                println(byteSize)

                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Upload", false) {
                    override fun run(indicator: ProgressIndicator) {
                        client.putObject(request, RequestBody.fromInputStream(fileChooser[0].inputStream, fileChooser[0].length))
                        treeTable.refresh(bucket)
                    }
                })

            } catch (e: Exception) {
                notifyError("Upload failed")
            }
        }

    }

    override fun isEnabled(): Boolean = true

    override fun updateButton(e: AnActionEvent) {}

    override fun isDumbAware(): Boolean = true

    @TestOnly
    fun uploadObjectAction(e: AnActionEvent, chooserDialog: FileChooserDialog) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
        val fileChooser = chooserDialog.choose(project, null)
        val request = PutObjectRequest.builder().bucket(bucket.key.bucket).key(fileChooser[0].name).build()
        client.putObject(request, RequestBody.fromInputStream(fileChooser[0].inputStream, fileChooser[0].length))
    }
}


