// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.documents

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info

internal data class DocumentMetadata(
    val uri: String,
    val fileName: String,
    val ext: String,
    val type: String,
    val cfnType: String,
    val languageId: String,
    val version: Int,
    val lineCount: Int,
)

@Service(Service.Level.PROJECT)
internal class CfnDocumentManager {
    private val documents = mutableListOf<DocumentMetadata>()

    fun getValidTemplates(): List<DocumentMetadata> =
        documents.filter { it.cfnType == "template" }

    fun updateDocuments(newDocuments: List<DocumentMetadata>) {
        LOG.info { "Updating documents to: $newDocuments" }
        documents.clear()
        documents.addAll(newDocuments)
    }

    companion object {
        private val LOG = getLogger<CfnDocumentManager>()

        fun getInstance(project: Project): CfnDocumentManager = project.service()
    }
}
