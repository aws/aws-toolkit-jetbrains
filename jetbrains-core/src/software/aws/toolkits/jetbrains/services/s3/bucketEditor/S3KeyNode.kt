// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.CachingSimpleNode
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.AwsClientManager
import java.time.Instant

open class S3KeyNode(project: Project, val bucketName: String, val parent: S3KeyNode?, val key: String) :
    CachingSimpleNode(project, null) {
    open val isDirectory = true

    private val client: S3Client = AwsClientManager.getInstance(project).getClient()
    private var cachedList: Array<S3KeyNode> = arrayOf()

    override fun buildChildren(): Array<S3KeyNode> = if (!isDirectory) {
        arrayOf()
    } else if (cachedList.isEmpty()) {
        loadObjects().toTypedArray()
    } else {
        cachedList
    }

    override fun getName(): String = if (this.isDirectory) key.dropLast(1).substringAfterLast('/') + '/' else key.substringAfterLast('/')

    fun loadMore(continuationToken: String) {
        cachedList = (children as Array<S3KeyNode>).dropLastWhile { it is S3ContinuationNode }.toTypedArray() + loadObjects(continuationToken)
        cleanUpCache()
    }

    private fun loadObjects(continuationToken: String? = null): List<S3KeyNode> {
        val response = client.listObjectsV2 {
            it.bucket(bucketName).delimiter("/").prefix(key)
            it.maxKeys(UPDATE_LIMIT)
            continuationToken?.apply { it.continuationToken(continuationToken) }
        }

        val continuation = listOfNotNull(response.nextContinuationToken()?.let {
            S3ContinuationNode(project!!, bucketName, this, this.key + '/' + "load more", it)
        })

        val folders = response.commonPrefixes()?.map { S3KeyNode(project!!, bucketName, this, it.prefix()) } ?: emptyList()

        val s3Objects = response
            .contents()
            ?.filterNotNull()
            ?.filterNot { it.key() == key }
            ?.map { S3ObjectNode(project!!, bucketName, this, it.key(), it.size(), it.lastModified()) as S3KeyNode }
            ?: emptyList()

        return (folders + s3Objects).sortedBy { it.key } + continuation
    }

    companion object {
        const val UPDATE_LIMIT = 300
    }
}

class S3ObjectNode(project: Project, bucketName: String, parent: S3KeyNode?, key: String, val size: Long, val lastModified: Instant) :
    S3KeyNode(project, bucketName, parent, key) {
    override val isDirectory: Boolean = false
}

class S3ContinuationNode(project: Project, bucketName: String, parent: S3KeyNode?, key: String, val token: String) :
    S3KeyNode(project, bucketName, parent, key) {
    override val isDirectory: Boolean = false
}
