// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.core

import software.amazon.q.core.credentials.ToolkitBearerTokenProvider
import software.amazon.q.core.credentials.ToolkitCredentialsProvider
import software.amazon.q.core.credentials.toEnvironmentVariables
import software.amazon.q.core.region.AwsRegion

sealed interface ClientConnectionSettings<out T> {
    val region: AwsRegion
    val providerId: String

    /**
     * Copies bean with the region replaced
     */
    fun withRegion(region: AwsRegion): ClientConnectionSettings<T>
}

data class ConnectionSettings(val credentials: ToolkitCredentialsProvider, override val region: AwsRegion) : ClientConnectionSettings<ConnectionSettings> {
    override val providerId: String
        get() = credentials.id

    override fun withRegion(region: AwsRegion) = copy(region = region)
}

data class TokenConnectionSettings(
    val tokenProvider: ToolkitBearerTokenProvider,
    override val region: AwsRegion,
) : ClientConnectionSettings<TokenConnectionSettings> {
    override val providerId: String
        get() = tokenProvider.id

    override fun withRegion(region: AwsRegion) = copy(region = region)
}

val ConnectionSettings.shortName get() = "${credentials.shortName}@${region.id}"

fun ConnectionSettings.toEnvironmentVariables(): Map<String, String> = region.toEnvironmentVariables() +
    credentials.resolveCredentials().toEnvironmentVariables()
