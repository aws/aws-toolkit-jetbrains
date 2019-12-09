// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.SimpleNode
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.s3.S3Directory

/**
 * Paginated S3KeyNode for TreeTable
 */
class S3KeyNode(project: Project, val bucketName: String, val parent: S3KeyNode?, val key: String) : SimpleNode(project) {
    override fun getChildren(): Array<S3KeyNode> {
        val client: S3Client = AwsClientManager.getInstance(project!!).getClient()

        return S3Directory(bucketName, key, client).children().sortedBy { it.key }
            .map {
                S3KeyNode(project!!, bucketName, this, it.key)
            }.toTypedArray()
    }

    override fun getName(): String = key

    companion object {
        const val UPDATE_LIMIT = 300
    }
}
