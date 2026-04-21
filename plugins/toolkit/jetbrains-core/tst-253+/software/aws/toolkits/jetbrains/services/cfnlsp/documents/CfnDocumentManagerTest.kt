// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.documents

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CfnDocumentManagerTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var documentManager: CfnDocumentManager

    @Before
    fun setUp() {
        documentManager = CfnDocumentManager.getInstance(projectRule.project)
    }

    @Test
    fun `getValidTemplates filters by cfnType template`() {
        val documents = listOf(
            createDocument("template.yaml", "template", "yaml"),
            createDocument("config.json", "other", "json"),
            createDocument("stack.yaml", "template", "yaml")
        )

        documentManager.updateDocuments(documents)

        val validTemplates = documentManager.getValidTemplates()
        assertThat(validTemplates).hasSize(2)
        assertThat(validTemplates.map { it.fileName }).containsExactly("template.yaml", "stack.yaml")
    }

    @Test
    fun `getValidTemplates returns empty list when no templates`() {
        val documents = listOf(
            createDocument("config.json", "other", "json"),
            createDocument("readme.md", "other", "md")
        )

        documentManager.updateDocuments(documents)

        val validTemplates = documentManager.getValidTemplates()
        assertThat(validTemplates).isEmpty()
    }

    @Test
    fun `updateDocuments replaces existing documents`() {
        val initialDocs = listOf(createDocument("old.yaml", "template", "yaml"))
        documentManager.updateDocuments(initialDocs)
        assertThat(documentManager.getValidTemplates()).hasSize(1)

        val newDocs = listOf(
            createDocument("new1.yaml", "template", "yaml"),
            createDocument("new2.yaml", "template", "yaml")
        )
        documentManager.updateDocuments(newDocs)

        val validTemplates = documentManager.getValidTemplates()
        assertThat(validTemplates).hasSize(2)
        assertThat(validTemplates.map { it.fileName }).containsExactly("new1.yaml", "new2.yaml")
    }

    @Test
    fun `requiresS3Upload returns false for small template below size limit`() {
        documentManager.updateDocuments(listOf(createDocument("small.yaml", "template", "yaml", sizeBytes = 1_024)))

        assertThat(documentManager.requiresS3Upload("file:///path/to/small.yaml")).isFalse()
    }

    @Test
    fun `requiresS3Upload returns false for template exactly at size limit`() {
        documentManager.updateDocuments(listOf(createDocument("edge.yaml", "template", "yaml", sizeBytes = 51_200)))

        assertThat(documentManager.requiresS3Upload("file:///path/to/edge.yaml")).isFalse()
    }

    @Test
    fun `requiresS3Upload returns true for template above size limit`() {
        documentManager.updateDocuments(listOf(createDocument("large.yaml", "template", "yaml", sizeBytes = 51_201)))

        assertThat(documentManager.requiresS3Upload("file:///path/to/large.yaml")).isTrue()
    }

    @Test
    fun `requiresS3Upload returns false when document metadata is not found`() {
        documentManager.updateDocuments(listOf(createDocument("other.yaml", "template", "yaml", sizeBytes = 60_000)))

        assertThat(documentManager.requiresS3Upload("file:///path/to/missing.yaml")).isFalse()
    }

    @Test
    fun `requiresS3Upload returns false when no documents have been received`() {
        assertThat(documentManager.requiresS3Upload("file:///path/to/anything.yaml")).isFalse()
    }

    @Test
    fun `requiresS3Upload defaults sizeBytes to 0 for backwards compatibility with old servers`() {
        // Simulates an older LSP server that doesn't send sizeBytes — Kotlin default of 0 applies
        documentManager.updateDocuments(listOf(createDocument("legacy.yaml", "template", "yaml")))

        assertThat(documentManager.requiresS3Upload("file:///path/to/legacy.yaml")).isFalse()
    }

    private fun createDocument(fileName: String, cfnType: String, languageId: String, sizeBytes: Int = 0) = DocumentMetadata(
        uri = "file:///path/to/$fileName",
        fileName = fileName,
        ext = fileName.substringAfterLast('.'),
        type = "document",
        cfnType = cfnType,
        languageId = languageId,
        version = 1,
        lineCount = 10,
        sizeBytes = sizeBytes
    )
}
