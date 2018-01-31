package software.aws.toolkits.jetbrains.aws.s3

import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.PathUtil
import software.amazon.awssdk.core.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class S3VirtualFileSystem(val s3Client: S3Client) : VirtualFileSystem() {
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        TODO("not implemented")
    }

    override fun getProtocol() = "s3"

    fun getBuckets(): List<S3BucketVirtualFile> = s3Client.listBuckets().buckets().map { S3BucketVirtualFile(this, it) }

    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        TODO("not implemented")
    }

    override fun addVirtualFileListener(listener: VirtualFileListener) {}

    override fun isReadOnly(): Boolean = true

    override fun findFileByPath(path: String): VirtualFile? {
        TODO("not implemented")
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        TODO("not implemented")
    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        TODO("not implemented")
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        TODO("not implemented")
    }

    override fun removeVirtualFileListener(listener: VirtualFileListener) {}

    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        TODO("not implemented")
    }

    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        TODO("not implemented")
    }

    override fun refresh(asynchronous: Boolean) {
        TODO("not implemented")
    }
}

class S3BucketVirtualFile(private val fileSystem: S3VirtualFileSystem, private val bucket: Bucket) : VirtualFile() {
    override fun getName(): String = bucket.name()

    override fun getFileSystem() = fileSystem

    override fun getPath() = name

    override fun isWritable() = false

    override fun isDirectory() = true

    override fun isValid() = true

    override fun getParent() = null

    val versioningEnabled by lazy { versioningStatus == BucketVersioningStatus.ENABLED }

    val versioningStatus by lazy { fileSystem.s3Client.getBucketVersioning{it.bucket(bucket.name())}.status() }

    val region: Region? by lazy {
        //TODO: Implement me
//        try {
//            Region.of(fileSystem.s3Client.getBucketLocation(bucket.name))
//        } catch (e: IllegalArgumentException) {
//            null
//        }
        null
    }

    override fun getChildren(): Array<VirtualFile> {
        val s3Client = fileSystem.s3Client

        val request = ListObjectsV2Request.builder().bucket(bucket.name()).delimiter("/").build()
        val children = arrayListOf<VirtualFile>()
        var continuationToken: String? = null

        do {
            val result = s3Client.listObjectsV2(request.copy({ it.continuationToken(continuationToken) }))

            children.addAll(result.commonPrefixes().map {
                S3VirtualDirectory(
                    fileSystem,
                    result.name(),
                    it.prefix(),
                    this
                )
            })

            children.addAll(result.contents().map {
                if (it.key().endsWith("/")) {
                    S3VirtualDirectory(fileSystem, bucket.name(), it.key(), this)
                } else {
                    S3VirtualFile(fileSystem, it, bucket.name(), this)
                }
            })

            continuationToken = result.nextContinuationToken()
        } while (result.isTruncated)

        return children.toTypedArray()
    }


    @Throws(IOException::class)
    override fun getOutputStream(requestor: Any, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw IOException("getOutputStream() cannot be called against a bucket")
    }

    @Throws(IOException::class)
    override fun contentsToByteArray(): ByteArray {
        throw IOException("contentsToByteArray() cannot be called against a bucket")
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        throw IOException("getInputStream() cannot be called against a bucket")
    }

    override fun getTimeStamp() = bucket.creationDate().toEpochMilli()

    override fun getLength() = -1L

    override fun getModificationStamp() = -1L

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is S3BucketVirtualFile && bucket.name() == other.bucket.name()
    }

    override fun hashCode() = bucket.name().hashCode()
}

abstract class BaseS3VirtualObject(
    protected val s3FileSystem: S3VirtualFileSystem,
    val bucketName: String,
    val key: String,
    private val _parent: VirtualFile
) : VirtualFile() {
    override fun getName() = PathUtil.getFileName(key)

    override fun getFileSystem() = s3FileSystem

    override fun getPath() = bucketName + "/" + key

    override fun getParent(): VirtualFile = _parent

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other?.javaClass != javaClass) {
            return false
        }

        other as BaseS3VirtualObject

        if (bucketName != other.bucketName || key != other.key) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = bucketName.hashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}

class S3VirtualDirectory(
    s3FileSystem: S3VirtualFileSystem,
    bucketName: String,
    directory: String,
    parent: VirtualFile
) : BaseS3VirtualObject(s3FileSystem, bucketName, directory, parent) {
    override fun getChildren(): Array<VirtualFile> {
        val s3Client = fileSystem.s3Client
        val request = ListObjectsV2Request.builder().bucket(bucketName).prefix(key).delimiter("/").build()
        val children = arrayListOf<VirtualFile>()
        var continuationToken: String? = null

        do {
            val result = s3Client.listObjectsV2(request.copy { it.continuationToken(continuationToken) })

            children.addAll(result.commonPrefixes().map { S3VirtualDirectory(fileSystem, bucketName, it.prefix(), this) })
            children.addAll(result.contents()
                .filterNot { it.key() == key }
                .map {
                    if (it.key().endsWith("/")) {
                        S3VirtualDirectory(fileSystem, bucketName, it.key(), this)
                    } else {
                        S3VirtualFile(fileSystem, it, bucketName, this)
                    }
                })

            continuationToken = result.nextContinuationToken()
        } while (result.isTruncated)

        return children.toTypedArray()
    }

    @Throws(IOException::class)
    override fun getOutputStream(requestor: Any, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw IOException("getOutputStream() cannot be called against a virtual directory")
    }

    @Throws(IOException::class)
    override fun contentsToByteArray(): ByteArray {
        throw IOException("contentsToByteArray() cannot be called against a virtual directory")
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        throw IOException("getInputStream() cannot be called against a virtual directory")
    }

    override fun isDirectory() = true

    override fun isWritable() = false

    override fun isValid() = true

    override fun getTimeStamp() = -1L

    override fun getLength() = -1L

    override fun getModificationStamp() = -1L
}

class S3VirtualFile(
    s3FileSystem: S3VirtualFileSystem,
    private val obj: S3Object,
    bucketName: String,
    _parent: VirtualFile
) : BaseS3VirtualObject(s3FileSystem, bucketName, obj.key(), _parent) {

    val eTag: String get() = obj.eTag()

    override fun getChildren(): Array<VirtualFile> = arrayOf()

    @Throws(IOException::class)
    override fun getOutputStream(requestor: Any, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        if (isDirectory) {
            throw IOException("getOutputStream() cannot be called against a directory")
        }

        TODO("not implemented")
    }

    @Throws(IOException::class)
    override fun contentsToByteArray(): ByteArray {
        if (FileUtilRt.isTooLarge(obj.size())) {
            throw FileTooBigException(path)
        }
        return inputStream.readBytes(obj.size().toInt())
    }

    @Throws(IOException::class)
    override fun getInputStream() =
        fileSystem.s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(obj.key()).build())

    override fun isDirectory() = key.endsWith("/")

    override fun isWritable() = false

    override fun isValid() = true

    override fun getTimeStamp() = obj.lastModified().toEpochMilli()

    override fun getLength() = obj.size()

    override fun getModificationStamp() = -1L
}