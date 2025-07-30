// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core

import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.credentials.toEnvironmentVariables
import software.aws.toolkits.core.region.AwsRegion

sealed interface ClientConnectionSettings<out T> {
    val region: AwsRegion
    val providerId: String

    /**
     * Copies bean with the region replaced
     */
    fun withRegion(region: AwsRegion): ClientConnectionSettings<T>
}

sealed interface TokenConnectionSettings: ClientConnectionSettings<TokenConnectionSettings> {
    val tokenProvider: ToolkitBearerTokenProvider
}

data class ConnectionSettings(val credentials: ToolkitCredentialsProvider, override val region: AwsRegion) : ClientConnectionSettings<ConnectionSettings> {
    override val providerId: String
        get() = credentials.id

    override fun withRegion(region: AwsRegion) = copy(region = region)
}

data class AwsTokenConnectionSettings(
    override val tokenProvider: ToolkitBearerTokenProvider,
    override val region: AwsRegion,
) : TokenConnectionSettings, ClientConnectionSettings<TokenConnectionSettings> {
    override val providerId: String
        get() = tokenProvider.id

    override fun withRegion(region: AwsRegion) = copy(region = region)
}

data class ExternalOidcTokenConnectionSettings(
    override val tokenProvider: ToolkitBearerTokenProvider,
    override val region: AwsRegion = AwsRegion.GLOBAL,
) : TokenConnectionSettings, ClientConnectionSettings<TokenConnectionSettings> {
    override val providerId: String
        get() = tokenProvider.id

    override fun withRegion(region: AwsRegion) = this
}

val ConnectionSettings.shortName get() = "${credentials.shortName}@${region.id}"

fun ConnectionSettings.toEnvironmentVariables(): Map<String, String> = region.toEnvironmentVariables() +
    credentials.resolveCredentials().toEnvironmentVariables()
