package software.aws.toolkits.core.s3

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectStorageClass
import java.time.Instant

/**
 * Extension to an [S3Client] for creating a [S3Bucket] resource.
 */
fun S3Client.bucket(name: String) = S3Bucket(name, this)

sealed class S3Key(val bucket: String, val key: String) {
    open val name = if (key.endsWith("/")) {
        key.dropLast(1)
    } else {
        key
    }.substringAfterLast("/")

    /**
     * A depth-first recursive walk of a tree of [S3Key]s, applying the [block] to each one.
     */
    fun walkTree(block: (S3Key) -> Unit) {
        walkTree({ true }, block)
    }

    /**
     * A depth-first recursive walk of a tree of [S3Key]s, applying the [block] to each one that matches the [filter].
     *
     * As soon as the [filter] fails, that branch is not traversed any further.
     * @param filter an inclusive filter to apply
     */
    fun walkTree(filter: (S3Key) -> Boolean, block: (S3Key) -> Unit) {
        if (filter(this)) {
            block(this)
            if (this is S3Directory) {
                this.children().forEach { it.walkTree(filter, block) }
            }
        }
    }
}

open class S3Directory internal constructor(bucket: String, key: String, private val client: S3Client) :
    S3Key(bucket, key) {
    fun children(): List<S3Key> {
        val request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .delimiter("/")
            .prefix(key)
            .build()
        return client.listObjectsV2Iterable(request).flatMap {
            val directories = (it.commonPrefixes() ?: emptyList()).map {
                S3Directory(
                    bucket = bucket,
                    key = it.prefix(),
                    client = client
                )
            }

            val objects = (it.contents() ?: emptyList()).filterNot { it.key() == key }.map {
                S3File(
                    bucket = bucket,
                    key = it.key(),
                    lastModified = it.lastModified(),
                    etag = it.eTag(),
                    storageClass = it.storageClass(),
                    size = it.size()
                )
            }

            directories + objects
        }
    }
}

class S3Bucket internal constructor(bucket: String, client: S3Client) : S3Directory(bucket, "", client) {
    override val name = bucket
}

class S3File internal constructor(
    bucket: String,
    key: String,
    val lastModified: Instant,
    val etag: String,
    val storageClass: ObjectStorageClass,
    val size: Long
) : S3Key(bucket, key)
