// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.TextDocumentIdentifier

// Resource Types
data class ResourceTypesResult(
    val resourceTypes: List<String>,
)

// Resource Listing
data class ResourceRequest(
    val resourceType: String,
    val nextToken: String? = null,
)

data class ListResourcesParams(
    val resources: List<ResourceRequest>? = null,
)

data class ResourceSummary(
    val typeName: String,
    val resourceIdentifiers: List<String>,
    val nextToken: String? = null,
)

data class ListResourcesResult(
    val resources: List<ResourceSummary>,
)

enum class ResourceStatePurpose(val value: String) {
    IMPORT("Import"),
    CLONE("Clone"),
}

data class ResourceSelection(
    val resourceType: String,
    val resourceIdentifiers: List<String>,
)

data class ResourceStateParams(
    val textDocument: TextDocumentIdentifier,
    val resourceSelections: List<ResourceSelection>? = null,
    val purpose: String,
    val parentResourceType: String? = null,
)

data class ResourceStateResult(
    val completionItem: CompletionItem? = null,
    val successfulImports: Map<String, List<String>>,
    val failedImports: Map<String, List<String>>,
    val warning: String? = null,
)

data class SearchResourceParams(
    val resourceType: String,
    val identifier: String,
)

data class SearchResourceResult(
    val found: Boolean,
    val resource: ResourceSummary? = null,
)

data class RefreshResourcesParams(
    val resources: List<ResourceRequest>,
)

data class RefreshResourcesResult(
    val resources: List<ResourceSummary>,
)

data class ResourceStackManagementResult(
    val physicalResourceId: String,
    val managedByStack: Boolean? = null,
    val stackName: String? = null,
    val stackId: String? = null,
    val error: String? = null
)
