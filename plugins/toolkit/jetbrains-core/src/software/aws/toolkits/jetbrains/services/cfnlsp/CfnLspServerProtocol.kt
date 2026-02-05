// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsResult
import java.util.concurrent.CompletableFuture

/**
 * Extended LSP server interface for CloudFormation Language Server.
 * Defines custom protocol methods beyond standard LSP.
 */
internal interface CfnLspServerProtocol : LanguageServer {
    @JsonRequest("aws/credentials/iam/update")
    fun updateIamCredentials(params: UpdateCredentialsParams): CompletableFuture<UpdateCredentialsResult>

    @JsonRequest("aws/cfn/stacks")
    fun listStacks(params: ListStacksParams): CompletableFuture<ListStacksResult>

    @JsonRequest("aws/cfn/stack/changeSet/list")
    fun listChangeSets(params: ListChangeSetsParams): CompletableFuture<ListChangeSetsResult>
}
