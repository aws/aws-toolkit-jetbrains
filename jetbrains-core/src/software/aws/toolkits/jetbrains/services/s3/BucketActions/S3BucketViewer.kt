// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.BucketActions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.S3BucketNode
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.S3VirtualFileSystem
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo

class S3BucketViewer : OpenBucketViewerAction<S3BucketNode>("Open Bucket in Viewer") {

    override fun openEditor(selected: S3BucketNode, client: S3Client, project: Project) {
        val editorManager = FileEditorManager.getInstance(project)
        val virtualBucket = S3VirtualBucket(S3VirtualFileSystem(client), selected.bucket)
        editorManager.openTextEditor(OpenFileDescriptor(project, virtualBucket), true)
    }

    override fun actionPerformed(selected: S3BucketNode, e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: S3Client = AwsClientManager.getInstance(project).getClient()
            try {
                openEditor(selected, client, project)
                TelemetryService.getInstance().record(project, "s3") {
                    datum("openeditor") {
                        count()
                    }
                }
                notifyInfo("successfully opened editor")
            } catch (e: Exception) {
                e.notifyError("Cannot open editor")
        }
    }
}