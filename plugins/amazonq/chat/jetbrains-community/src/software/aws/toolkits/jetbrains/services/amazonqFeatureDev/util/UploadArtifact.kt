// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

import com.intellij.util.io.HttpRequests
import software.amazon.awssdk.utils.IoUtils
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.amazonq.APPLICATION_ZIP
import software.aws.toolkits.jetbrains.services.amazonq.AWS_KMS
import software.aws.toolkits.jetbrains.services.amazonq.CONTENT_SHA256
import software.aws.toolkits.jetbrains.services.amazonq.SERVER_SIDE_ENCRYPTION
import software.aws.toolkits.jetbrains.services.amazonq.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevOperation
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.UploadCodeException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.UploadURLExpired
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients.FeatureDevClient
import java.io.File
import java.net.HttpURLConnection

private val logger = getLogger<FeatureDevClient>()
fun uploadArtifactToS3(url: String, fileToUpload: File, checksumSha256: String, contentLength: Long, kmsArn: String?) {
    try {
        HttpRequests.put(url, APPLICATION_ZIP).userAgent(AwsClientManager.getUserAgent()).tuner {
            it.setRequestProperty("Content-Type", APPLICATION_ZIP)
            it.setRequestProperty("Content-Length", contentLength.toString())
            it.setRequestProperty(CONTENT_SHA256, checksumSha256)
            if (!kmsArn.isNullOrEmpty()) {
                it.setRequestProperty(SERVER_SIDE_ENCRYPTION, AWS_KMS)
                it.setRequestProperty(SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID, kmsArn)
            }
        }
            .connect {
                val connection = it.connection as HttpURLConnection
                connection.setFixedLengthStreamingMode(fileToUpload.length())
                IoUtils.copy(fileToUpload.inputStream(), connection.outputStream)
            }
    } catch (err: HttpRequests.HttpStatusException) {
        logger.warn(err) { "$FEATURE_NAME: Failed to upload code to S3" }
        when (err.statusCode) {
            403 -> throw UploadURLExpired(operation = FeatureDevOperation.UploadToS3.toString(), desc = "Upload URL expired or forbidden")
            else -> throw UploadCodeException(operation = FeatureDevOperation.UploadToS3.toString(), desc = "Failed to upload code to S3")
        }
    }
}

fun deleteUploadArtifact(payload: File) {
    if (!payload.delete()) {
        logger.warn { "Unable to delete upload artifact." }
    }
}
