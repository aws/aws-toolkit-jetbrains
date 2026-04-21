// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.documents

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn

internal data class DocumentMetadata(
    val uri: String,
    val fileName: String,
    val ext: String,
    val type: String,
    val cfnType: String,
    val languageId: String,
    val version: Int,
    val lineCount: Int,
    val sizeBytes: Int = 0,
)

@Service(Service.Level.PROJECT)
internal class CfnDocumentManager {
    private val documents = mutableListOf<DocumentMetadata>()

    fun getValidTemplates(): List<DocumentMetadata> =
        documents.filter { it.cfnType == "template" }

    fun requiresS3Upload(uri: String): Boolean {
        val doc = documents.find { it.uri == uri }
        if (doc == null) {
            LOG.warn { "Document metadata not found for URI: $uri. Assuming no S3 upload required may lead to deployment failure." }
            return false
        }
        return doc.sizeBytes > CFN_TEMPLATE_BODY_MAX_BYTES
    }

    fun updateDocuments(newDocuments: List<DocumentMetadata>) {
        LOG.info { "Updating documents to: $newDocuments" }
        documents.clear()
        documents.addAll(newDocuments)
    }

    companion object {
        private const val CFN_TEMPLATE_BODY_MAX_BYTES = 51_200
        private val LOG = getLogger<CfnDocumentManager>()

        fun getInstance(project: Project): CfnDocumentManager = project.service()
    }
}
