// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import software.amazon.awssdk.services.s3.model.Bucket
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class S3VirtualBucket(val s3Bucket: Bucket) : LightVirtualFile() {
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
    override fun isDirectory(): Boolean = true
}
