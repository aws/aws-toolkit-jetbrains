// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.elementType
import java.awt.event.MouseEvent
import kotlin.math.min


// disabled, register if need to bring back the code visions
class InlineChatCodeVisionProvider: CodeVisionProvider<Unit> {
    companion object {
        internal const val id: String = "amazonq.chat.code.vision"
    }
    override val id: String = Companion.id
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
    override val name: String = "AmazonQ Chat Code Vision"
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    override fun precomputeOnUiThread(editor: Editor) {
    }

    override fun shouldRecomputeForEditor(editor: Editor, uiData: Unit?): Boolean = true

    override fun isAvailableFor(project: Project): Boolean = true


    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
            return runReadAction {
                val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
                val project = editor.project ?: return@runReadAction READY_EMPTY
                val document = editor.document
                val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@runReadAction READY_EMPTY
                val traverser = SyntaxTraverser.psiTraverser(file)

                val text = "Amazon Q: Edit \u2318 + I"
                val elements = traverser.preOrderDfsTraversal().filter { element ->
                    val elementType = element.elementType.toString().toLowerCase()
                    elementType.contains("function") ||
                        elementType.contains("class")
                }
                val clickHandler :
                        (MouseEvent?, Editor)-> Unit = { e: MouseEvent?, _ ->
                    InlineChatController(editor, editor.project!!).initPopup()
                }
                FloatingToolbarProvider
                for (element in elements) {
                    val textRange = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
                    val length = editor.document.textLength
                    val adjustedRange = TextRange(min(textRange.startOffset, length), min(textRange.endOffset, length))
                    val entry = ClickableTextCodeVisionEntry(text, id, clickHandler, icon = null, text, text, emptyList())
                    lenses.add(Pair(adjustedRange, entry))
                }
                return@runReadAction CodeVisionState.Ready(lenses)
            }
    }
}
