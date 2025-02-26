// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.auth

import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.util.concurrent.CompletableFuture

interface AuthCredentialsService {
    fun updateTokenCredentials(accessToken: String, encrypted: Boolean): CompletableFuture<ResponseMessage>
    fun deleteTokenCredentials(): CompletableFuture<Unit>
}
