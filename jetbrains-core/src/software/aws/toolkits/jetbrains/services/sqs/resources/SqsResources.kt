// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.resources

import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource

object SqsResources {
    @JvmField
    val LIST_QUEUES = ClientBackedCachedResource(SqsClient::class, "sqs.list_queues") {
            listQueues().queueUrls().toList()
    }

}
