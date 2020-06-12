// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift

import software.amazon.awssdk.services.redshift.model.Cluster
import software.aws.toolkits.core.region.AwsRegion

private val REDSHIFT_REGION_REGEX = """.*\..*\.(.+).redshift\.""".toRegex()
private val REDSHIFT_IDENTIFIER_REGEX = """.*//(.+)\..*\..*.redshift\..""".toRegex()

fun clusterArn(cluster: Cluster, region: AwsRegion) = "arn:${region.partitionId}:redshift:${region.id}::cluster:${cluster.clusterIdentifier()}"
fun extractRegionFromUrl(url: String?): String? = url?.let { REDSHIFT_REGION_REGEX.find(url)?.groupValues?.get(1) }
fun extractClusterIdFromUrl(url: String?): String? = url?.let { REDSHIFT_IDENTIFIER_REGEX.find(url)?.groupValues?.get(1) }
