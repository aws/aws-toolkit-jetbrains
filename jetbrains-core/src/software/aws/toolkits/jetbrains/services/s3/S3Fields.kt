package software.aws.toolkits.jetbrains.services.s3

import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

//S3 Key class represents a base class for S3 Directory and S3 Objects

sealed class S3Key(
        val bucket: String,
        val key: String
) {
    open val name = if (key.endsWith("/")) {
        key.dropLast(1)
    } else {
        key.substringAfterLast("/")
    }
}

class S3Bucket(
        override val bucketName: String,
        val client: S3Client,
        val creationDate: Instant
) : S3Directory(bucketName, "", client) {
    override val name: String = bucketName
}

class S3Object(
        val bucketName: String,
        key: String,
        val eTag: String,
        val size: Long,
        val lastModified: Instant,
        val client: S3Client
) : S3Key(bucketName, key)


open class S3Directory(
        open val bucketName: String,
        key: String,
        private val client: S3Client
) : S3Key(bucketName, key) {


    fun children(): List<S3Key>? {

        val request = ListObjectsV2Request.builder().bucket(bucketName)
                .delimiter("/").prefix(key).build()
        val response = client.listObjectsV2(request)

        //gives common prefixed folders
        val folders = (response!!.commonPrefixes() ?: emptyList())
                .map { S3Directory(bucketName, it.prefix(), client) }
        val s3Objects = (response.contents()
                ?: emptyList()).filterNotNull().filterNot { it.key() == key }
                .map { S3Object(bucketName, it.key(), it.eTag(), it.size(), it.lastModified(), client) }

        return folders + s3Objects
    }
}



