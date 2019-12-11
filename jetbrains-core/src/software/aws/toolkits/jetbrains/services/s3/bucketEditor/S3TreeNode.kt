// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.SimpleNode
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.resources.message
import java.time.Instant

abstract class S3TreeNode(project: Project, val bucketName: String, val parent: S3TreeNode?, val key: String) : SimpleNode(project, null) {
    open val isDirectory = false
    override fun getChildren(): Array<S3TreeNode> = arrayOf()
    override fun getName(): String = key.substringAfterLast('/')
}

class S3TreeDirectoryNode(project: Project, bucketName: String, parent: S3TreeNode?, key: String) : S3TreeNode(project, bucketName, parent, key) {
    override val isDirectory = true
    private val lock = Object()
    private val loadedPages = mutableSetOf<String>()
    private val client: S3Client = AwsClientManager.getInstance(project).getClient()
    private var cachedList: Array<S3TreeNode> = arrayOf()

    override fun getName(): String = key.dropLast(1).substringAfterLast('/') + '/'
    override fun getChildren(): Array<S3TreeNode> {
        synchronized(lock) {
            if (cachedList.isEmpty()) {
                cachedList = loadObjects().toTypedArray()
            }
        }
        return cachedList
    }

    @Synchronized
    fun loadMore(continuationToken: String) {
        // dedupe calls
        if (loadedPages.contains(continuationToken)) {
            return
        }
        cachedList = children.dropLastWhile { it is S3TreeContinuationNode }.toTypedArray() + loadObjects(continuationToken)
        loadedPages.add(continuationToken)
    }

    private fun loadObjects(continuationToken: String? = null): List<S3TreeNode> {
        val response = client.listObjectsV2 {
            it.bucket(bucketName).delimiter("/").prefix(key)
            it.maxKeys(MAX_ITEMS_TO_LOAD)
            continuationToken?.apply { it.continuationToken(continuationToken) }
        }

        val continuation = listOfNotNull(response.nextContinuationToken()?.let {
            // Spaces are intentional
            S3TreeContinuationNode(project!!, bucketName, this, "${this.key}/     ${message("s3.load_more")}", it)
        })

        val folders = response.commonPrefixes()?.map { S3TreeDirectoryNode(project!!, bucketName, this, it.prefix()) } ?: emptyList()

        val s3Objects = response
            .contents()
            ?.filterNotNull()
            ?.filterNot { it.key() == key }
            ?.map { S3TreeObjectNode(project!!, bucketName, this, it.key(), it.size(), it.lastModified()) as S3TreeNode }
            ?: emptyList()

        return (folders + s3Objects).sortedBy { it.key } + continuation
    }

    fun removeChild(node: S3TreeNode) {
        cachedList = cachedList.filter { it != node }.toTypedArray()
    }

    fun removeAllChildren() {
        cachedList = arrayOf()
    }

    companion object {
        const val MAX_ITEMS_TO_LOAD = 300
    }
}

class S3TreeObjectNode(project: Project, bucketName: String, parent: S3TreeNode?, key: String, val size: Long, val lastModified: Instant) :
    S3TreeNode(project, bucketName, parent, key) {
}

class S3TreeContinuationNode(project: Project, bucketName: String, parent: S3TreeNode?, key: String, val token: String) :
    S3TreeNode(project, bucketName, parent, key) {
}
