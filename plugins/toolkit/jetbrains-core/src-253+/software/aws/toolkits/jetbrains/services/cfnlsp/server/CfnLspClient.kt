// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import software.aws.toolkits.jetbrains.services.cfnlsp.documents.CfnDocumentManager
import software.aws.toolkits.jetbrains.services.cfnlsp.documents.DocumentMetadata

internal class CfnLspClient(
    handler: LspServerNotificationsHandler,
    private val project: Project,
) : Lsp4jClient(handler) {

    @JsonNotification("aws/documents/metadata")
    fun onDocumentsMetadata(documents: Array<DocumentMetadata>) {
        val documentManager = CfnDocumentManager.getInstance(project)
        documentManager.updateDocuments(documents.toList())
    }
}
