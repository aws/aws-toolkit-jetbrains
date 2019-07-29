// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.core.s3.regionForBucket
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerService
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsNodeAlwaysExpandable
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class S3ServiceNode(project: Project) : AwsExplorerServiceRootNode(project, AwsExplorerService.S3),
        AwsNodeAlwaysExpandable {
    private val client: S3Client = AwsClientManager.getInstance(project).getClient()

    override fun loadResources(paginationToken: String?): Collection<AwsExplorerNode<*>> {
        val activeRegionId = ProjectAccountSettingsManager.getInstance(nodeProject).activeRegion.id
        val response = client.listBuckets().buckets()
                .asSequence()
                .filter { client.regionForBucket(it.name()) == activeRegionId }

        val allS3Buckets = response.map { S3Bucket(bucket = it.name(), client = client, creationDate = it.creationDate()) }
        return allS3Buckets
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.bucket })
                .map { mapResourceToNode(it) }
                .toList()
    }

    private fun mapResourceToNode(resource: S3Bucket) = S3BucketNode(nodeProject, resource, client)
}

class S3BucketNode(project: Project, val bucket: S3Bucket, val client: S3Client) :
        AwsExplorerResourceNode<String>(project, S3Client.SERVICE_NAME, bucket.bucket, AwsIcons.Resources.CLOUDFORMATION_STACK) {

    private val editorManager = FileEditorManager.getInstance(project)

    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    override fun resourceType(): String = "bucket"

    override fun resourceArn() = "arn:aws:s3:::${bucket.bucket}"

    override fun onDoubleClick(model: DefaultTreeModel, selectedElement: DefaultMutableTreeNode) {
        val virtualBucket = S3VirtualBucket(S3VirtualFileSystem(client), bucket)
        editorManager.openTextEditor(OpenFileDescriptor(nodeProject, virtualBucket), true)

        TelemetryService.getInstance().record(nodeProject, "s3") {
            datum("openeditor") {
                count()
            }
        }
    }
    override fun toString(): String = bucket.bucket

}