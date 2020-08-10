// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources

import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.TagFilter
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.filtering.ResourceTagFilter

object ResourceGroupsTaggingApiResources {
    fun listResources(serviceId: String, resourceType: String? = null, tags: Map<String, ResourceTagFilter>? = null) =
        ClientBackedCachedResource(ResourceGroupsTaggingApiClient::class, "resourceGroupsTaggingApi.$serviceId.$resourceType.${tags?.toString()}}") {
            getResourcesPaginator { request ->
                // S3 is special, bucket resourcetype doesn't exist
                val resourceTypeFilter = if (serviceId == S3Client.SERVICE_NAME || resourceType == null) {
                    serviceId
                } else {
                    "$serviceId:$resourceType"
                }
                request.resourceTypeFilters(resourceTypeFilter)
                tags?.forEach {
                    if (it.value.enabled) {
                        request.tagFilters(TagFilter.builder().key(it.key).values(it.value.values).build())
                    }
                }
            }
        }
}
