// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.resources

import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.Topic
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.Resource

object SnsResources {
    private val LIST_TOPICS: Resource.Cached<List<Topic>> = ClientBackedCachedResource(SnsClient::class, "sns.list_topics") {
        listTopicsPaginator().topics().toList()
    }

    val LIST_TOPIC_NAMES: Resource<List<SnsTopic>> = Resource.View(LIST_TOPICS) {
        map { SnsTopic(it.topicArn()) }
    }
}

data class SnsTopic(val arn: String) {
    val name = arn.substringAfterLast(':')
    override fun toString(): String = name
}
