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

    private fun createDocument(fileName: String, cfnType: String, languageId: String) = DocumentMetadata(
        uri = "file:///path/to/$fileName",
        fileName = fileName,
        ext = fileName.substringAfterLast('.'),
        type = "document",
        cfnType = cfnType,
        languageId = languageId,
        version = 1,
        lineCount = 10
    )
}
