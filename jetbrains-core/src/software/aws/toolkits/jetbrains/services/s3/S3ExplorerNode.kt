// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Bucket
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerService
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.resources.S3Resources
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService

class S3ServiceNode(project: Project) : AwsExplorerServiceRootNode(project, AwsExplorerService.S3) {
    private val activeRegionId = ProjectAccountSettingsManager.getInstance(nodeProject).activeRegion.id

    override fun getChildrenInternal(): List<AwsExplorerNode<*>> =
        AwsResourceCache.getInstance(nodeProject).getResourceNow(S3Resources.LIST_BUCKETS)
            .filter { AwsResourceCache.getInstance(nodeProject).getResourceNow(S3Resources.bucketRegion(it.name())) == activeRegionId }
            .map { S3BucketNode(nodeProject, it) }
            .toList()
}

class S3BucketNode(project: Project, val bucket: Bucket) :
    AwsExplorerResourceNode<String>(project, S3Client.SERVICE_NAME, bucket.name(), AwsIcons.Resources.S3_BUCKET) {

    override fun resourceType(): String = "bucket"

    override fun resourceArn() = "arn:aws:s3:::${bucket.name()}"

    override fun isAlwaysShowPlus(): Boolean = false

    override fun onDoubleClick() {
        if (!DumbService.getInstance(nodeProject).isDumb) {
            val editorManager = FileEditorManager.getInstance(nodeProject)
            // See if there is already an open editor, otherwise make a new one
            val virtualFile =
                editorManager.openFiles.firstOrNull { (it as? S3VirtualBucket)?.s3Bucket?.equals(bucket) == true } ?: S3VirtualBucket(
                    bucket
                )
            editorManager.openTextEditor(OpenFileDescriptor(nodeProject, virtualFile), true)
            recordOpenTelemetry()
        }
    }

    private fun recordOpenTelemetry() = TelemetryService.getInstance().record(nodeProject) {
        datum("s3_openeditor") {
            count()
        }
    }

    override fun displayName(): String = bucket.name()
}
