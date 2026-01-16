// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/**
 * Extended LSP server interface for CloudFormation Language Server.
 *
 * Defines custom protocol methods beyond standard LSP. New protocol methods
 * should be added here following the pattern:
 * - Requests: @JsonRequest with CompletableFuture return type
 * - Notifications: @JsonNotification with Unit return type
 */
interface CfnLspServer : LanguageServer {

    // ========================================
    // Auth: aws/credentials/iam/*
    // ========================================

    @JsonRequest("aws/credentials/iam/update")
    fun updateIamCredentials(params: UpdateCredentialsParams): CompletableFuture<UpdateCredentialsResult>

    @JsonNotification("aws/credentials/iam/delete")
    fun deleteIamCredentials()
}

// ========================================
// Auth Types
// ========================================

data class UpdateCredentialsParams(
    val data: String,
    val encrypted: Boolean = true
)

data class UpdateCredentialsResult(
    val success: Boolean
)
