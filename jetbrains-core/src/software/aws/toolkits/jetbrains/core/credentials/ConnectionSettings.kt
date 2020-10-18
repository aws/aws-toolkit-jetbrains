// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.tryOrNull

data class ConnectionSettings(val credentials: ToolkitCredentialsProvider, val region: AwsRegion)

fun ConnectionSettings.safelyApplyTo(env: MutableMap<String, String>) {
    region.toEnvironmentVariables().forEach { (key, value) -> env.putIfAbsent(key, value) }
    if (env.containsKey("AWS_ACCESS_KEY") || env.containsKey("AWS_ACCESS_KEY_ID")) {
        // We don't want to override the existing settings - and we can't just do putIfAbsent because that might leave the env-vars in an
        // inconsistent state (e.g. AWS_ACCESS_KEY is set but AWS_SESSION_TOKEN is not)
        return
    }
    tryOrNull { credentials.resolveCredentials() }?.toEnvironmentVariables()?.let { env.putAll(it) }
}

val ConnectionSettings.shortName get() = "${credentials.shortName}@${region.id}"
