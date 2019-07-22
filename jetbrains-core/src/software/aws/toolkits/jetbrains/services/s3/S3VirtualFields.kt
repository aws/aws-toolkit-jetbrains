// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

// base class to represent a virtual file
abstract class BaseS3VirtualFile(
    val fileSystem: S3VFS,
    private val parent: VirtualFile?,
    open val key: S3Key
) : VirtualFile() {
    override fun getName(): String = key.key

    override fun isWritable(): Boolean = false

    override fun getPath(): String = "${key.bucket}/${key.key}"

    override fun isValid(): Boolean = true
    override fun getParent(): VirtualFile? = parent

    override fun toString(): String = "${key.key}"

    override fun getFileSystem(): VirtualFileSystem = fileSystem
    override fun getLength(): Long = 0

    override fun getTimeStamp(): Long = 0

    override fun contentsToByteArray(): ByteArray {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun getInputStream(): InputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}

class S3VirtualFile(
    s3Vfs: S3VFS,
    val file: S3Object,
    parent: VirtualFile
) : BaseS3VirtualFile(s3Vfs, parent, file) {

    override fun isDirectory(): Boolean = false

    override fun getChildren(): Array<VirtualFile> = emptyArray()

    override fun getLength(): Long = file.size

    override fun getTimeStamp(): Long = file.lastModified.toEpochMilli()

    override fun contentsToByteArray(): ByteArray {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun getInputStream(): InputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}
}

open class S3VirtualBucket(
    fileSystem: S3VFS,
    val s3Bucket: S3Bucket
) : BaseS3VirtualFile(fileSystem, parent = null, key = s3Bucket) {

    override fun getTimeStamp(): Long = s3Bucket.creationDate.toEpochMilli()

    fun getCreationDate(): Instant = s3Bucket.creationDate

    fun getVirtualBucketName(): String = s3Bucket.name

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

    override fun getChildren(): Array<VirtualFile> =
        s3Bucket.children()!!.sortedBy { it.name }
            .map {
                when (it) {
                    is S3Object -> S3VirtualFile(fileSystem, it, this)
                    is S3Directory -> S3VirtualDirectory(fileSystem, it, this)
                }
            }.toTypedArray()

    override fun isDirectory(): Boolean = true
}

class S3VirtualDirectory(
    s3filesystem: S3VFS,
    private val directory: S3Directory,
    parent: VirtualFile
) : BaseS3VirtualFile(s3filesystem, parent, directory) {
    override fun getChildren(): Array<VirtualFile> =
        directory.children()!!.sortedBy { it.name }.filterNot { it.key == directory.key }
            .map {
                when (it) {
                    is S3Object -> S3VirtualFile(fileSystem, it, this)
                    is S3Directory -> S3VirtualDirectory(fileSystem, it, this)
                }
            }.toTypedArray()

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}
    override fun isDirectory(): Boolean = true
}