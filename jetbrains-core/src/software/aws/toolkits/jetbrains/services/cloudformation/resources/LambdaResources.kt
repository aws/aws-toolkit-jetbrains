// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.resources

import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackResourceSummary
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.core.CachedResource
import software.aws.toolkits.jetbrains.core.CachedResourceBase

object CloudFormationResources {
    private val listStacks: CachedResource<List<StackSummary>> by lazy {
        CloudFormationCachedResource { listStacksPaginator().stackSummaries().toList() }
    }

    fun listStacks() = listStacks

    fun listStackResources(stackId: String): CachedResource<List<StackResourceSummary>> =
        CloudFormationStackResourcesCachedResource(stackId)

    private class CloudFormationCachedResource<T>(private val call: CloudFormationClient.() -> T) :
        CachedResourceBase<T, CloudFormationClient>(CloudFormationClient::class) {
        override fun fetch(client: CloudFormationClient): T = call(client)
    }

    private class CloudFormationStackResourcesCachedResource(
        private val stackId: String
    ) : CachedResourceBase<List<StackResourceSummary>, CloudFormationClient>(CloudFormationClient::class) {
        override fun fetch(client: CloudFormationClient): List<StackResourceSummary> =
            client.listStackResourcesPaginator { it.stackName(stackId) }.stackResourceSummaries().toList()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CloudFormationStackResourcesCachedResource

            if (stackId != other.stackId) return false

            return true
        }

        override fun hashCode(): Int {
            return stackId.hashCode()
        }


    }
}