// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.project.Project
import icons.AwsIcons
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor

import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.core.s3.regionForBucket
import software.aws.toolkits.jetbrains.core.AwsClientManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.explorer.AwsNodeAlwaysExpandable


class S3ServiceNode(project: Project) : AwsExplorerServiceRootNode(project, "S3"),
        AwsNodeAlwaysExpandable {
    override fun serviceName() = S3Client.SERVICE_NAME
    private val client: S3Client = AwsClientManager.getInstance(project).getClient()

    override fun loadResources(paginationToken: String?): Collection<AwsExplorerNode<*>> {
        val activeRegionId = ProjectAccountSettingsManager.getInstance(project!!).activeRegion.id
        val response = client.listBuckets().buckets()
                .asSequence()
                .filter { client.regionForBucket(it.name()) == activeRegionId }

        val allS3Buckets = response.map { S3Bucket(bucketName = it.name(), client = client, creationDate = it.creationDate()) }
        return allS3Buckets
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { mapResourceToNode(it) }
                .toList()
    }

    private fun mapResourceToNode(resource: S3Bucket) = S3BucketNode(project!!, resource)

}


class S3BucketNode(project: Project, val bucket: S3Bucket) :
        AwsExplorerResourceNode<String>(project, S3Client.SERVICE_NAME, bucket.name, AwsIcons.Resources.CLOUDFORMATION_STACK) {

    val s3Bucket: S3Bucket = bucket
    private val editorManager = FileEditorManager.getInstance(project)
    val client: S3Client = AwsClientManager.getInstance(project).getClient()

    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    override fun resourceType(): String = "bucket"

    override fun onDoubleClick(model: DefaultTreeModel, selectedElement: DefaultMutableTreeNode) {
        val bucketVirtualFile = S3VirtualBucket(S3VFS(client), bucket)
        editorManager.openTextEditor(OpenFileDescriptor(project!!, bucketVirtualFile), true)
    }
    override fun toString(): String = bucket.name
}


