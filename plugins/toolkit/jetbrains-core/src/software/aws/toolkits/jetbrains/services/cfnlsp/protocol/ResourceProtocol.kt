// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.TextDocumentIdentifier

// Resource Types
internal data class ResourceTypesResult(
    val resourceTypes: List<String>,
)

// Resource Listing
internal data class ResourceRequest(
    val resourceType: String,
    val nextToken: String? = null,
)

internal data class ListResourcesParams(
    val resources: List<ResourceRequest>? = null,
)

internal data class ResourceSummary(
    val typeName: String,
    val resourceIdentifiers: List<String>,
    val nextToken: String? = null,
)

internal data class ListResourcesResult(
    val resources: List<ResourceSummary>,
)

internal enum class ResourceStatePurpose(val value: String) {
    IMPORT("Import"),
    CLONE("Clone"),
}

internal data class ResourceSelection(
    val resourceType: String,
    val resourceIdentifiers: List<String>,
)

internal data class ResourceStateParams(
    val textDocument: TextDocumentIdentifier,
    val resourceSelections: List<ResourceSelection>? = null,
    val purpose: String,
    val parentResourceType: String? = null,
)

internal data class ResourceStateResult(
    val completionItem: CompletionItem? = null,
    val successfulImports: Map<String, List<String>>,
    val failedImports: Map<String, List<String>>,
    val warning: String? = null,
)

internal data class SearchResourceParams(
    val resourceType: String,
    val identifier: String,
)

internal data class SearchResourceResult(
    val found: Boolean,
    val resource: ResourceSummary? = null,
)

internal data class RefreshResourcesParams(
    val resources: List<ResourceRequest>,
)

internal data class RefreshResourcesResult(
    val resources: List<ResourceSummary>,
)

internal data class ResourceStackManagementResult(
    val physicalResourceId: String,
    val managedByStack: Boolean? = null,
    val stackName: String? = null,
    val stackId: String? = null,
    val error: String? = null,
)
