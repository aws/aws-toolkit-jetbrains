// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws

interface GetConfigurationFromServerResponse

// This represents the entire array
data class Workspaces(val workspaces: List<WorkspaceInfo>): GetConfigurationFromServerResponse
data class Customizations(val customizations: List<Customization>): GetConfigurationFromServerResponse

data class WorkspaceInfo(val workspaceRoot: String, val workspaceId: String)
data class Customization(val arn: String, val name: String, val description: String)
