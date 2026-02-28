// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.core.utils.Waiters
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val LSP_REQUEST_TIMEOUT_S = 30L

/**
 * Manages CloudFormation LSP server lifecycle and document operations for integration tests.
 */
internal class CfnLspTestFixture(private val fixture: CodeInsightTestFixture) {

    private var schemasReady = false

    fun tearDown() = fixture.tearDown()

    fun openTemplate(name: String, content: String): VirtualFile {
        var file: VirtualFile? = null
        runWriteActionAndWait { file = fixture.tempDirFixture.createFile(name, content) }
        val vf = file!!
        runInEdtAndWait { fixture.openFileInEditor(vf) }
        ensureRunning()
        ensureSchemasLoaded()

        request { lsp ->
            lsp.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(fileUri(vf), "yaml", 1, content))
            )
            CompletableFuture.completedFuture(Unit)
        }
        waitForDocumentProcessed(vf)
        return vf
    }

    fun fileUri(file: VirtualFile): String = file.toNioPath().toUri().toString()

    fun <T> request(block: (LanguageServer) -> CompletableFuture<T>): T {
        val future = CompletableFuture<T>()
        runningServer().sendNotification { lsp ->
            block(lsp).whenComplete { result, error ->
                if (error != null) future.completeExceptionally(error)
                else future.complete(result)
            }
        }
        return future.get(LSP_REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
    }

    private fun runningServer() =
        LspServerManager.getInstance(fixture.project)
            .getServersForProvider(CfnLspServerDescriptor.providerClass())
            .first { it.state == LspServerState.Running }

    private fun ensureRunning() {
        val providerClass = CfnLspServerDescriptor.providerClass()
        LspServerManager.getInstance(fixture.project)
            .ensureServerStarted(providerClass, CfnLspServerDescriptor.getInstance(fixture.project))

        runBlocking {
            Waiters.waitUntil(
                succeedOn = { it },
                maxDuration = Duration.ofSeconds(120),
            ) {
                val servers = LspServerManager.getInstance(fixture.project).getServersForProvider(providerClass)
                servers.any { it.state == LspServerState.Running }
            }
        } ?: throw AssertionError("CloudFormation LSP server did not reach Running state")
    }

    /**
     * Calls aws/cfn/resources/types to confirm public schemas have been loaded.
     */
    private fun ensureSchemasLoaded() {
        if (schemasReady) return

        val protocolClass = Class.forName("software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspServerProtocol")
        val listResourceTypes = protocolClass.getMethod("listResourceTypes")

        runBlocking {
            Waiters.waitUntil(
                succeedOn = { it },
                maxDuration = Duration.ofSeconds(60),
            ) {
                val future = CompletableFuture<Boolean>()
                runningServer().sendNotification { lsp ->
                    if (protocolClass.isInstance(lsp)) {
                        @Suppress("UNCHECKED_CAST")
                        val resultFuture = listResourceTypes.invoke(lsp) as CompletableFuture<Any?>
                        resultFuture.whenComplete { result, error ->
                            val types = result?.javaClass?.getMethod("getResourceTypes")?.invoke(result) as? List<*>
                            future.complete(error == null && types?.isNotEmpty() == true)
                        }
                    } else {
                        future.complete(false)
                    }
                }
                future.get(LSP_REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
            }
        } ?: throw AssertionError("CloudFormation LSP schemas not loaded")

        schemasReady = true
    }

    /**
     * Requests document symbols to confirm the server has parsed the document after didOpen.
     */
    private fun waitForDocumentProcessed(file: VirtualFile) {
        runBlocking {
            Waiters.waitUntil(
                succeedOn = { it == true },
                maxDuration = Duration.ofSeconds(10),
            ) {
                val result = request { lsp ->
                    lsp.textDocumentService.documentSymbol(
                        DocumentSymbolParams(TextDocumentIdentifier(fileUri(file)))
                    )
                }
                result?.isNotEmpty()
            }
        } ?: throw AssertionError("Document not processed by server")
    }
}
