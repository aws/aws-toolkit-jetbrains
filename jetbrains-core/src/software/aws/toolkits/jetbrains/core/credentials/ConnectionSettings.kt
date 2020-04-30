// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.buildMap

data class ConnectionSettings(val credentials: ToolkitCredentialsProvider, val region: AwsRegion)

fun ConnectionSettings.toEnvironmentVariables(): Map<String, String> = buildMap {
    putAll(region.toEnvironmentVariables())
    putAll(credentials.resolveCredentials().toEnvironmentVariables())
}
