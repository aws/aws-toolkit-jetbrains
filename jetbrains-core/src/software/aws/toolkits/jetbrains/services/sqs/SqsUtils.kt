// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.SqsException
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.telemetry.SqsQueueType

const val MAX_NUMBER_OF_POLLED_MESSAGES = 10
const val MAX_LENGTH_OF_POLLED_MESSAGES = 1024
const val MAX_LENGTH_OF_FIFO_ID = 128
const val MAX_LENGTH_OF_QUEUE_NAME = 80

// Maximum length of queue name is 80, but the maximum will be 75 for FIFO queues due to '.fifo' suffix
const val MAX_LENGTH_OF_FIFO_QUEUE_NAME = 75

// Extension function to get telemetry type from Queue
fun Queue.telemetryType() = if (isFifo) SqsQueueType.Fifo else SqsQueueType.Standard

/*
 * Get the approximate number of messages from a queue. Returns null when there is a service exception
 * thrown, or the value returned is not an int.
 * @param queueUrl The queue url to retrieve the approximate number of messages from
 */
fun SqsClient.approximateNumberOfMessages(queueUrl: String): Int? = try {
    getQueueAttributes {
        it.queueUrl(queueUrl)
        it.attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
    }.attributes().getValue(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).toIntOrNull()
} catch (e: SqsException) {
    getLogger<SqsClient>().error(e) { "SqsClient threw an exception getting approximate number of messages" }
    null
}
