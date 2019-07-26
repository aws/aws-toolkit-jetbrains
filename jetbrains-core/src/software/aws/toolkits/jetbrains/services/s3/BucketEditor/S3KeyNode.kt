// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.BucketEditor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.SimpleNode
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory

class S3KeyNode(val virtualFile: VirtualFile) : SimpleNode() {

    override fun getChildren(): Array<S3KeyNode> {
        when (virtualFile) {
            is S3VirtualBucket -> return virtualFile.children.map { S3KeyNode(it) }.toTypedArray()
            is S3VirtualDirectory -> return virtualFile.children.map { S3KeyNode(it) }.toTypedArray()
            else -> return emptyArray()
        }
    }

    override fun getName(): String {
        when (virtualFile) {
            is S3VirtualBucket -> return virtualFile.getVirtualBucketName()
            else -> return virtualFile.name
        }
    }
}