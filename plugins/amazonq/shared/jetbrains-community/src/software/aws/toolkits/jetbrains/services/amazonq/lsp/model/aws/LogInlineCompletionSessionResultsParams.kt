// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws
import software.amazon.awssdk.services.codewhispererruntime.model.IdeDiagnostic

data class LogInlineCompletionSessionResultsParams(
    val sessionId: String,
    val completionSessionResult: Map<String, InlineCompletionStates>,
    val firstCompletionDisplayLatency: Double?,
    val totalSessionDisplayTime: Double?,
    val typeaheadLength: Long,
    val addedDiagnostics: List<IdeDiagnostic>? = emptyList(),
    val removedDiagnostics: List<IdeDiagnostic>? = emptyList(),

)
