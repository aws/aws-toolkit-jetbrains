// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials

import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspConstants

data class ConnectionMetadata(
    val sso: SsoProfileData,
) {
    companion object {
        fun fromConnection(connection: ToolkitConnection) = when (connection) {
            is AwsBearerTokenConnection -> {
                ConnectionMetadata(
                    SsoProfileData(connection.startUrl)
                )
            }
            else -> {
                // If no connection or not a bearer token connection return default builderID start url
                ConnectionMetadata(
                    SsoProfileData(AmazonQLspConstants.AWS_BUILDER_ID_URL)
                )
            }
        }
    }
}

data class SsoProfileData(
    val startUrl: String,
)
