// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds

import software.amazon.awssdk.services.rds.model.DBCluster

data class RdsDatasourceConfiguration(
    val regionId: String,
    val credentialId: String,
    val dbCluster: DBCluster,
    val username: String
)
