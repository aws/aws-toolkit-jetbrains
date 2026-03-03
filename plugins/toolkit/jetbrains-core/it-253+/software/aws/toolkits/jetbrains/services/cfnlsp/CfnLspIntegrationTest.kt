// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * E2E integration test for CloudFormation LSP.
 * Downloads the real language server and validates autocomplete, hover,
 * go-to-definition, and document symbols via direct LSP protocol requests.
 *
 * Requires Node.js 18+ on PATH. Hard-fails if not found.
 */
class CfnLspIntegrationTest {

    private lateinit var lsp: CfnLspTestFixture

    @Before
    fun setUp() {
        val nodeAvailable = try {
            val process = ProcessBuilder("node", "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val version = process.inputStream.bufferedReader().readText().trim()
                version.removePrefix("v").split(".").firstOrNull()?.toIntOrNull()?.let { it >= 18 } ?: false
            } else false
        } catch (_: Exception) { false }

        assertThat(nodeAvailable)
            .withFailMessage("Node.js 18+ is required to run CloudFormation LSP integration tests")
            .isTrue()

        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val projectFixture = factory.createLightFixtureBuilder("CfnLspIntegTest").fixture
        val codeFixture = factory.createCodeInsightFixture(projectFixture)
        codeFixture.setUp()
        lsp = CfnLspTestFixture(codeFixture)
    }

    @After
    fun tearDown() {
        lsp.tearDown()
    }

    @Test
    fun `autocomplete provides CloudFormation top-level sections`() {
        val file = lsp.openTemplate("top-level.yaml", "AWSTemplateFormatVersion: \"2010-09-09\"\n")
        val labels = lspComplete(lsp.fileUri(file), Position(1, 0))
        assertThat(labels).anyMatch { it.contains("Resources") || it.contains("Parameters") || it.contains("Outputs") }
    }

    @Test
    fun `autocomplete provides resource types`() {
        val file = lsp.openTemplate("resource-type.yaml",
            "AWSTemplateFormatVersion: \"2010-09-09\"\nResources:\n  MyBucket:\n    Type: ")
        val labels = lspComplete(lsp.fileUri(file), Position(3, 10))
        assertThat(labels).anyMatch { it.startsWith("AWS::") }
    }

    @Test
    fun `autocomplete provides resource properties`() {
        val file = lsp.openTemplate("resource-props.yaml",
            "AWSTemplateFormatVersion: \"2010-09-09\"\nResources:\n  MyBucket:\n    Type: AWS::S3::Bucket\n    Properties:\n      ")
        val labels = lspComplete(lsp.fileUri(file), Position(5, 6))
        assertThat(labels).anyMatch { it.contains("BucketName") }
    }

    @Test
    fun `hover provides documentation for resource types`() {
        val file = lsp.openTemplate("hover-resource.yaml",
            "AWSTemplateFormatVersion: \"2010-09-09\"\nResources:\n  MyBucket:\n    Type: AWS::S3::Bucket")
        val hover = lsp.request { it.textDocumentService.hover(HoverParams(TextDocumentIdentifier(lsp.fileUri(file)), Position(3, 15))) }
        assertThat(hover).isNotNull()
    }

    @Test
    fun `go-to-definition navigates to parameter from Ref`() {
        val file = lsp.openTemplate("definition-param.yaml",
            "AWSTemplateFormatVersion: \"2010-09-09\"\nParameters:\n  MyParam:\n    Type: String\nResources:\n  MyBucket:\n    Type: AWS::S3::Bucket\n    Properties:\n      BucketName: !Ref MyParam")
        val definition = lsp.request { it.textDocumentService.definition(DefinitionParams(TextDocumentIdentifier(lsp.fileUri(file)), Position(8, 25))) }
        assertThat(definition).isNotNull()
    }

    @Test
    fun `document symbols provides template outline`() {
        val file = lsp.openTemplate("symbols.yaml",
            "AWSTemplateFormatVersion: \"2010-09-09\"\nParameters:\n  MyParam:\n    Type: String\nResources:\n  MyBucket:\n    Type: AWS::S3::Bucket\nOutputs:\n  BucketName:\n    Value: !Ref MyBucket")
        val symbols = lsp.request { it.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(lsp.fileUri(file)))) }
        assertThat(symbols).isNotNull()
        assertThat(symbols).isNotEmpty()
    }

    private fun lspComplete(uri: String, position: Position): List<String> {
        val result = lsp.request { it.textDocumentService.completion(CompletionParams(TextDocumentIdentifier(uri), position)) }
            ?: return emptyList()
        val items = if (result.isLeft) result.left else result.right?.items
        return items?.map { it.label } ?: emptyList()
    }
}
