// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws

// This represents each item in the array
data class WorkspaceInfo(val workspaceRoot: String, val workspaceId: String)

// This represents the entire array
data class LspServerConfigurations(val workspaces: List<WorkspaceInfo>)
