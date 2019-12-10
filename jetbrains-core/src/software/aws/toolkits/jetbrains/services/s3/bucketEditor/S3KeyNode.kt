// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.SimpleNode
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.AwsClientManager
import java.time.Instant

open class S3KeyNode(project: Project, val bucketName: String, val parent: S3KeyNode?, val key: String) : SimpleNode(project) {
    private val client: S3Client = AwsClientManager.getInstance(project).getClient()
    private val initialChildren: List<S3KeyNode> by lazy { loadObjects() }
    private var additionalObjects: List<S3KeyNode> = listOf()

    override fun getChildren(): Array<S3KeyNode> =
        (initialChildren + additionalObjects).sortedBy { it.key }
            .map {
                S3KeyNode(project!!, bucketName, this, it.key)
            }.toTypedArray()

    override fun getName(): String = key

    fun loadMore(continuationToken: String?) {
        additionalObjects = additionalObjects + loadObjects(continuationToken)
    }

    private fun loadObjects(continuationToken: String? = null): List<S3KeyNode> {
        val response = client.listObjectsV2 {
            it.bucket(bucketName).delimiter("/").prefix(key)
            it.maxKeys(UPDATE_LIMIT)
            it.continuationToken(continuationToken)
        }

        val continuation = listOfNotNull(response.continuationToken()?.let {
            S3ContinuationNode(project!!, bucketName, this, this.key + '/' + "load more", it)
        })

        val folders = response.commonPrefixes()?.map { S3KeyNode(project!!, bucketName, this, it.prefix()) } ?: emptyList()

        val s3Objects = response
            .contents()
            ?.filterNotNull()
            ?.filterNot { it.key() == key }
            ?.map { S3ObjectNode(project!!, bucketName, this, it.key(), it.size(), it.lastModified()) as S3KeyNode }
            ?: emptyList()

        return folders + s3Objects + continuation
    }

    companion object {
        const val UPDATE_LIMIT = 300
    }
}

class S3ObjectNode(project: Project, bucketName: String, parent: S3KeyNode?, key: String, val size: Long, val lastModified: Instant) :
    S3KeyNode(project, bucketName, parent, key) {
}

class S3ContinuationNode(project: Project, bucketName: String, parent: S3KeyNode?, key: String, val token: String) :
    S3KeyNode(project, bucketName, parent, key) {}
