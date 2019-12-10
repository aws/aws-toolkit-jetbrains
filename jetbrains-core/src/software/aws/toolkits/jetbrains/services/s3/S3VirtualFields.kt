// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Bucket
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class S3VirtualBucket(private val fileSystem: S3VirtualFileSystem, val s3Bucket: Bucket) : VirtualFile() {
    fun formatDate(date: Instant): String {
        val datetime = LocalDateTime.ofInstant(date, ZoneId.systemDefault())
        return datetime.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d YYYY hh:mm:ss a z"))
    }

    override fun getName(): String = s3Bucket.name()

    override fun isWritable(): Boolean = false

    override fun getPath(): String = s3Bucket.name()

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? = null

    override fun toString(): String = s3Bucket.name()

    override fun getFileSystem(): VirtualFileSystem = fileSystem

    override fun getLength(): Long = 0

    override fun getTimeStamp(): Long = 0

    override fun contentsToByteArray(): ByteArray {
        throw UnsupportedOperationException("contentsToByteArray() cannot be called against this object type")
    }

    override fun getInputStream(): InputStream {
        throw UnsupportedOperationException("getInputStream() cannot be called against this object type")
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw UnsupportedOperationException("getOutputStream() cannot be called against this object type")
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

    val client: S3Client = fileSystem.client

    override fun getChildren(): Array<VirtualFile> = arrayOf()

    override fun isDirectory(): Boolean = true
}
