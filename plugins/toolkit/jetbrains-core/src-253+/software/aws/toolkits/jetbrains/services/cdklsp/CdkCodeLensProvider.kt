// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cdklsp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cdklsp.server.CdkLspServerSupportProvider
import java.util.concurrent.TimeUnit

/**
 * Bridges the CDK language server's `textDocument/codeLens` into IntelliJ
 * CodeVision, because the platform LSP API does not surface codeLens.
 *
 * Implemented as a frontend [CodeVisionProvider] (not DaemonBound) so its lenses
 * can be recomputed on demand via `CodeVisionHost.invalidateProvider` after a
 * server-state change (the auto-synth toggle) without requiring an editor edit.
 * A DaemonBound provider caches against the PSI modification stamp, which a
 * server-side toggle doesn't change, so its label would stay stale until an edit.
 *
 * Flow: fetch the server's codeLenses for the file (off the EDT) and render each
 * as a clickable CodeVision entry. The `cdkExplorer.openResource` lens is handled
 * client-side (navigate to the synthesized-template location); the auto-synth
 * lenses round-trip to the server via workspace/executeCommand.
 */
internal class CdkCodeLensProvider : CodeVisionProvider<VirtualFile?> {
    override val name: String get() = "AWS CDK resources"
    override val id: String get() = "aws.cdk.codeLens"
    override val groupId: String get() = id
    override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()
    override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Top

    // Runs on the UI thread: cheaply capture the file for the background compute.
    override fun precomputeOnUiThread(editor: Editor): VirtualFile? =
        FileDocumentManager.getInstance().getFile(editor.document)

    override fun computeCodeVision(editor: Editor, uiData: VirtualFile?): CodeVisionState {
        val project = editor.project ?: return CodeVisionState.READY_EMPTY
        val vFile = uiData ?: return CodeVisionState.READY_EMPTY

        val server = LspServerManager.getInstance(project)
            .getServersForProvider(CdkLspServerSupportProvider::class.java)
            .firstOrNull() ?: return CodeVisionState.READY_EMPTY

        // The server object exists before its lsp4j connection is initialized.
        // Issuing a request too early throws UninitializedPropertyAccessException
        // (lsp4jServerConnector). Only ask once it's fully connected.
        if (server.state != LspServerState.Running) return CodeVisionState.READY_EMPTY

        val uri = server.descriptor.getFileUri(vFile)
        val lenses = try {
            // Round-trip a standard lsp4j request through the running server.
            val future = java.util.concurrent.CompletableFuture<List<org.eclipse.lsp4j.CodeLens>?>()
            server.sendNotification { lsp ->
                lsp.textDocumentService.codeLens(CodeLensParams(TextDocumentIdentifier(uri)))
                    .whenComplete { result, error ->
                        if (error != null) future.completeExceptionally(error) else future.complete(result)
                    }
            }
            future.get(2, TimeUnit.SECONDS).orEmpty()
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to fetch CDK codeLenses for $uri" }
            return CodeVisionState.READY_EMPTY
        }

        val doc = editor.document
        val entries = lenses.mapNotNull { lens ->
            val command = lens.command ?: return@mapNotNull null
            // LSP ranges can be stale relative to the (possibly edited) document.
            // Skip anything out of bounds rather than throwing out of the whole pass.
            val startLine = lens.range.start.line
            val endLine = lens.range.end.line
            if (startLine !in 0 until doc.lineCount || endLine !in 0 until doc.lineCount) {
                return@mapNotNull null
            }
            val start = (doc.getLineStartOffset(startLine) + lens.range.start.character).coerceIn(0, doc.textLength)
            val end = (doc.getLineStartOffset(endLine) + lens.range.end.character).coerceIn(start, doc.textLength)
            val entry = ClickableTextCodeVisionEntry(
                text = command.title,
                providerId = id,
                onClick = { _, clickEditor -> onLensClick(project, command, clickEditor) }
            )
            TextRange(start, end) to entry
        }
        return CodeVisionState.Ready(entries)
    }

    /** Route a lens click: openResource navigates client-side; other commands are server executeCommands. */
    private fun onLensClick(project: Project, command: Command, editor: Editor) {
        LOG.info { "CDK CodeLens clicked: ${command.command}" }
        if (command.command == OPEN_RESOURCE_COMMAND) {
            onOpenResource(project, command.arguments)
        } else {
            executeServerCommand(project, command, editor)
        }
    }

    /** Forward a server command (synthNow / enable/disableAutoSynth) via workspace/executeCommand. */
    private fun executeServerCommand(project: Project, command: Command, editor: Editor) {
        val server = LspServerManager.getInstance(project)
            .getServersForProvider(CdkLspServerSupportProvider::class.java)
            .firstOrNull() ?: return
        if (server.state != LspServerState.Running) {
            LOG.info { "CDK LSP not running; skipping command ${command.command}" }
            return
        }
        LOG.info { "Forwarding CDK LSP executeCommand: ${command.command}" }
        server.sendNotification { lsp ->
            lsp.workspaceService.executeCommand(ExecuteCommandParams(command.command, command.arguments.orEmpty()))
                .whenComplete { _, _ ->
                    // The command toggled server-side state (e.g. auto-synth on/off).
                    // Recompute this provider's lenses so the enable<->disable label
                    // updates without an edit. A daemon restart won't do it: CodeVision
                    // caches entries against the (unchanged) PSI modification stamp, so
                    // invalidateProvider is the API that forces the re-query.
                    ApplicationManager.getApplication().invokeLater {
                        project.service<CodeVisionHost>()
                            .invalidateProvider(CodeVisionHost.LensInvalidateSignal(editor, listOf(id)))
                    }
                }
        }
    }

    /** Client-side handler for `cdkExplorer.openResource`: navigate to the chosen target. */
    private fun onOpenResource(project: Project, arguments: List<Any?>?) {
        val choices = parseChoices(arguments)
        when {
            choices.isEmpty() -> return
            choices.size == 1 -> navigate(project, choices.first())
            else -> JBPopupFactory.getInstance()
                .createPopupChooserBuilder(choices)
                .setTitle("Open resource in synthesized template")
                .setItemChosenCallback { navigate(project, it) }
                .createPopup()
                .showInFocusCenter()
        }
    }

    private fun navigate(project: Project, choice: ResourceChoice) {
        val vFile = VirtualFileManager.getInstance().findFileByUrl(choice.uri) ?: return
        OpenFileDescriptor(project, vFile, choice.line, choice.character).navigate(true)
    }

    private data class ResourceChoice(val label: String, val description: String, val uri: String, val line: Int, val character: Int) {
        // Rendered in the chooser popup.
        override fun toString(): String = if (description.isBlank()) label else "$label  \u2014  $description"
    }

    /**
     * The server sends `arguments: [ [ { label, description, target: { uri, range:{start:{line,character}} } } ] ]`.
     * lsp4j hands these back as gson JsonElements.
     */
    private fun parseChoices(arguments: List<Any?>?): List<ResourceChoice> {
        val first = arguments?.firstOrNull() as? JsonElement ?: return emptyList()
        val array = when {
            first.isJsonArray -> first.asJsonArray
            else -> return emptyList()
        }
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val target = obj.getAsJsonObject("target") ?: return@mapNotNull null
            val start = target.getAsJsonObject("range")?.getAsJsonObject("start") ?: return@mapNotNull null
            ResourceChoice(
                label = obj.primitiveString("label").orEmpty(),
                description = obj.primitiveString("description").orEmpty(),
                uri = target.primitiveString("uri") ?: return@mapNotNull null,
                line = start.primitiveInt("line") ?: 0,
                character = start.primitiveInt("character") ?: 0
            )
        }
    }

    // gson `.asString`/`.asInt` throw on JsonNull or non-primitives; guard first.
    private fun JsonObject.primitiveString(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.primitiveInt(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt

    companion object {
        private val LOG = getLogger<CdkCodeLensProvider>()

        // The one lens command handled client-side; everything else round-trips to the server.
        private const val OPEN_RESOURCE_COMMAND = "cdkExplorer.openResource"
    }
}
