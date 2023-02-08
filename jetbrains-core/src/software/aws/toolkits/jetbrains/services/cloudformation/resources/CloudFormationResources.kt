// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.resources

import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.ResourceStatus
import software.amazon.awssdk.services.cloudformation.model.StackResourceSummary
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.Resource

object CloudFormationResources {
    @JvmField
    // "Active" stacks means everything that is not deleted
    val ACTIVE_STACKS: Resource.Cached<List<StackSummary>> = ClientBackedCachedResource(CloudFormationClient::class, "cloudformation.list_active_stacks") {
        listStacksPaginator {
            it.stackStatusFilters(
                // We want all values except for DELETE_COMPLETE
                StackStatus.knownValues().toList().filter { status -> status != StackStatus.DELETE_COMPLETE }
            )
        }.stackSummaries().toList().filter { it.stackName() != null }
    }

    fun stackResources(stackName: String): Resource.Cached<List<StackResourceSummary>> =
        ClientBackedCachedResource(CloudFormationClient::class, "cloudformation.stack_resources($stackName)") {
            listStackResourcesPaginator {
                it.stackName(stackName)
            }.stackResourceSummaries().filter {
                it.resourceStatus() == ResourceStatus.CREATE_COMPLETE ||
                    it.resourceStatus() == ResourceStatus.UPDATE_COMPLETE ||
                    it.resourceStatus() == ResourceStatus.ROLLBACK_COMPLETE
            }
        }
}
