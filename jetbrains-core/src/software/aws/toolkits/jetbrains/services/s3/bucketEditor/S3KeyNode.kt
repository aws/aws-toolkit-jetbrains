// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.SimpleNode
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory

class S3KeyNode(val virtualFile: VirtualFile) : SimpleNode() {

    override fun getChildren(): Array<S3KeyNode> = when (virtualFile) {
        is S3VirtualBucket, is S3VirtualDirectory -> virtualFile.children
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { S3KeyNode(it) }
            .toTypedArray()
        else -> emptyArray()
    }

    override fun getName(): String = when (virtualFile) {
        is S3VirtualBucket -> virtualFile.getVirtualBucketName()
        else -> virtualFile.name
    }
}