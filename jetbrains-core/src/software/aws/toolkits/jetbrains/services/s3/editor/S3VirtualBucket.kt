// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import software.amazon.awssdk.services.s3.model.Bucket

class S3VirtualBucket(val s3Bucket: Bucket) : LightVirtualFile() {
    override fun getName(): String = s3Bucket.name()
    override fun isWritable(): Boolean = false
    override fun getPath(): String = s3Bucket.name()
    override fun isValid(): Boolean = true
    override fun getParent(): VirtualFile? = null
    override fun toString(): String = s3Bucket.name()
    override fun isDirectory(): Boolean = true
}

// See if there is already an open editor, otherwise make a new one
fun getOrCreateS3VirtualBucketFile(project: Project, bucket: Bucket): VirtualFile =
    FileEditorManager.getInstance(project).openFiles.firstOrNull { (it as? S3VirtualBucket)?.s3Bucket?.equals(bucket) == true } ?: S3VirtualBucket(bucket)
