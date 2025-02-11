// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import java.util.concurrent.CompletableFuture

/**
 * Requests sent by server to client
 */
@Suppress("unused")
interface AmazonQLanguageClient : LanguageClient {
    @JsonRequest("aws/credentials/getConnectionMetadata")
    fun getConnectionMetadata(): CompletableFuture<ConnectionMetadata>
}
