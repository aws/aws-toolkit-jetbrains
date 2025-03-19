// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.codewhisperer

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionContext
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionTriggerKind
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionWithReferencesParams
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import com.intellij.openapi.project.Project

class AWSTextDocumentServiceHandler(
    private val project: Project,
    serverInstance: Disposable
) : LookupManagerListener {

    init {
        project.messageBus.connect(serverInstance).subscribe(
            LookupManagerListener.TOPIC,
            this
        )
    }

    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (oldLookup != null || newLookup == null) return

        newLookup.addLookupListener(object : LookupListener {
            override fun itemSelected(event: LookupEvent) {
                val editor = event.lookup.editor
                if (!(event.lookup as LookupImpl).isShown) {
                    cleanup()
                    return
                }

                handleInlineCompletion(editor)
                cleanup()
            }

            override fun lookupCanceled(event: LookupEvent) {
                cleanup()
            }

            private fun cleanup() {
                newLookup.removeLookupListener(this)
            }
        })
    }

    private fun handleInlineCompletion(editor: Editor) {
        AmazonQLspService.executeIfRunning(project) { server ->
            val params = buildInlineCompletionParams(editor)
            server.inlineCompletionWithReferences(params)
        }
    }

    private fun buildInlineCompletionParams(editor: Editor): InlineCompletionWithReferencesParams {
        return InlineCompletionWithReferencesParams().apply {
            textDocument = TextDocumentIdentifier(getDocumentUri(editor))
            position = Position(
                editor.caretModel.logicalPosition.line,
                editor.caretModel.logicalPosition.column
            )
            context = InlineCompletionContext().apply {
                triggerKind = InlineCompletionTriggerKind.Invoke
            }
        }
    }
}
