// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import software.aws.toolkits.core.region.AwsRegion
import java.lang.IllegalArgumentException

/* This does not support Federal Information Processing Standard (FIPS) */
class Queue(queueUrl: String, region: AwsRegion) {
    val accountId = queueUrl.substringAfter("${region.id}")
        ?.substringAfter("/")
        ?.substringBefore("/")
        ?: throw IllegalArgumentException()
    val queueName = queueUrl.substringAfter("$accountId/") ?: throw IllegalArgumentException()
    val arn = "arn:${region.partitionId}:sqs:${region.id}:$accountId:$queueName"

    fun String.substringAfter(delimiter: String): String? {
        val str = substringAfter(delimiter, this)
        return if (str == this) {
            null
        } else {
            str
        }
    }
}
