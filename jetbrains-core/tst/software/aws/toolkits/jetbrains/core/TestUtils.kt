// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.project.Project
import com.nhaarman.mockitokotlin2.mock
import software.amazon.awssdk.services.s3.model.Bucket
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.services.s3.resources.S3Resources

fun MockResourceCacheRule.fillResourceCache(project: Project) {
    this.addEntry(
        project,
        EcsResources.LIST_CLUSTER_ARNS,
        listOf("arn2", "arn3")
    )

    this.addEntry(
        project,
        S3Resources.LIST_REGIONALIZED_BUCKETS,
        listOf(S3Resources.RegionalizedBucket(Bucket.builder().name("abc").build(), AwsRegion.GLOBAL))
    )

    this.addEntry(
        project,
        makeMockList("arn2"),
        listOf("service1", "service2")
    )

    this.addEntry(
        project,
        makeMockList("arn3"),
        listOf("service1", "service2")
    )
}

fun makeMockList(clusterArn: String): Resource.Cached<List<String>> = mock {
    on { id }.thenReturn("ecs.list_services.$clusterArn")
}
