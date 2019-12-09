// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.SimpleNode
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory

/**
 * Paginated S3KeyNode for TreeTable
 */
class S3KeyNode(val virtualFile: VirtualFile) : SimpleNode() {
    var prev = 0
    var next = Math.min(UPDATE_LIMIT, virtualFile.children.size)
    var currSize = 0
    var prevSize = 0

    override fun getChildren(): Array<S3KeyNode> {
        return when (virtualFile) {
            is S3VirtualBucket -> virtualFile.children
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { S3KeyNode(it) }
                .toTypedArray().sliceArray(prev..(next - 1))
            is S3VirtualDirectory -> virtualFile.children
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { S3KeyNode(it) }
                .toTypedArray()
            else -> emptyArray()
        }
    }

    override fun getName(): String = when (virtualFile) {
        is S3VirtualBucket -> virtualFile.getVirtualBucketName()
        else -> virtualFile.name
    }

    companion object {
        /**
         * Page Limits
         */
        const val UPDATE_LIMIT = 300
        const val START_SIZE = 0
    }
}
