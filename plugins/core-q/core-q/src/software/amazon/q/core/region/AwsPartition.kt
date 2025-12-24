// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.core.region

data class AwsPartition(val id: String, val description: String, val regions: Collection<AwsRegion>) {
    val displayName = "$description ($id)"
}
