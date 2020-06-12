// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift

import software.amazon.awssdk.services.redshift.model.Cluster
import software.aws.toolkits.core.region.AwsRegion

fun clusterArn(cluster: Cluster, region: AwsRegion) = "arn:${region.partitionId}:redshift:${region.id}::cluster:${cluster.clusterIdentifier()}"
