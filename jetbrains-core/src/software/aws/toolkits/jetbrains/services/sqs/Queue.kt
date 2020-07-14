// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import software.aws.toolkits.core.region.AwsRegion
import java.lang.IllegalArgumentException
import software.aws.toolkits.resources.message

/* This does not support Federal Information Processing Standard (FIPS) */
class Queue(val queueUrl: String, val region: AwsRegion) {
    val accountId: String = queueUrl.substringAfter("${region.id}").substringAfter("/").substringBefore("/")
        get() {
            if ((field == queueUrl) || (field.length != 12) || field.isBlank()) {
                throw IllegalArgumentException(message("sqs.url.parse_error"))
            } else {
                return field
            }
        }

    val queueName: String = queueUrl.substringAfter("$accountId/")
        get() {
            if (field == queueUrl || field.isBlank()) {
                throw IllegalArgumentException(message("sqs.url.parse_error"))
            } else {
                return field
            }
        }

    val arn = "arn:${region.partitionId}:sqs:${region.id}:$accountId:$queueName"
}
