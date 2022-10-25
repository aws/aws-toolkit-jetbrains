// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import software.aws.toolkits.core.ClientConnectionSettings
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.TokenConnectionSettings

sealed interface ToolkitConnection {
    val id: String
    val label: String

    fun getConnectionSettings(): ClientConnectionSettings<*>
}

interface AwsCredentialConnection : ToolkitConnection {
    override fun getConnectionSettings(): ConnectionSettings
}

interface AwsBearerTokenConnection : ToolkitConnection {
    override fun getConnectionSettings(): TokenConnectionSettings
}

interface BearerSsoConnection : AwsBearerTokenConnection {
    val scopes: List<String>
}

sealed interface AuthProfile

data class ManagedSsoProfile(
    var ssoRegion: String,
    var startUrl: String,
    var scopes: List<String>
) : AuthProfile {
    // only used for serializer
    constructor() : this("", "", emptyList())
}

interface ToolkitAuthManager {
    fun listConnections(): List<ToolkitConnection>

    fun createConnection(profile: AuthProfile): ToolkitConnection

    fun deleteConnection(connection: ToolkitConnection)
    fun deleteConnection(connectionId: String)

    fun getConnection(connectionId: String): ToolkitConnection?
}

interface ToolkitConnectionManager {
    fun activeConnection(): ToolkitConnection?

    fun switchConnection(connection: ToolkitConnection)
}
