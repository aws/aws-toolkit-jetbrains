// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata

data class ExtendedClientMetadata(
    val aws: AwsMetadata,
)

data class AwsMetadata(
    val clientInfo: ClientInfoMetadata,
    val awsClientCapabilities: AwsClientCapabilities,
    val contextConfiguration: ContextConfiguration?,
)

data class AwsClientCapabilities(
    val q: QCapabilities,
    val window: WindowCapabilities,
)

data class QCapabilities(
    val developerProfiles: Boolean,
    val mcp: Boolean,
    val pinnedContextEnabled: Boolean,
    val workspaceFilePath: String?,
)

data class WindowCapabilities(
    val showSaveFileDialog: Boolean,
)

data class ClientInfoMetadata(
    val extension: ExtensionMetadata,
    val clientId: String,
    val version: String,
    val name: String,
)

data class ExtensionMetadata(
    val name: String,
    val version: String,
)

data class ContextConfiguration(
    val workspaceIdentifier: String?,
)

fun createExtendedClientMetadata(project: Project): ExtendedClientMetadata {
    val metadata = ClientMetadata.getDefault()
    return ExtendedClientMetadata(
        aws = AwsMetadata(
            clientInfo = ClientInfoMetadata(
                extension = ExtensionMetadata(
                    name = metadata.awsProduct.toString(),
                    version = metadata.awsVersion
                ),
                clientId = metadata.clientId,
                version = metadata.parentProductVersion,
                name = metadata.parentProduct
            ),
            awsClientCapabilities = AwsClientCapabilities(
                q = QCapabilities(
                    developerProfiles = true,
                    mcp = true,
                    pinnedContextEnabled = true,
                    workspaceFilePath = project.workspaceFile?.path,
                ),
                window = WindowCapabilities(
                    showSaveFileDialog = true
                )
            ),
            contextConfiguration = ContextConfiguration(
                workspaceIdentifier = project.getBasePath()
            )
        )
    )
}
