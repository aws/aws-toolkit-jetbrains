// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.history.integration.ui.views.RevisionsList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.toolbar.floating.EditorFloatingToolbar
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import java.awt.event.MouseEvent
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class InlineChatCodeVisionProvider: CodeVisionProvider<Unit> {
    companion object {
        internal const val id: String = "amazonq.chat.code.vision"
    }
    override val id: String = Companion.id
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
    override val name: String = "AmazonQ Chat Code Vision"
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
//    private var selectionListener: SelectionListener? = null
//    private var lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()


//    override fun precomputeOnUiThread(editor: Editor) {
//    }

    override fun precomputeOnUiThread(editor: Editor) {
        // Remove any existing listener
//        selectionListener?.let { editor.selectionModel.removeSelectionListener(it) }
//
//        // Create a new listener
//        selectionListener = object : SelectionListener {
//            override fun selectionChanged(e: SelectionEvent) {
//                // Trigger a refresh of code visions
//                editor.project?.let { project ->
//                    DaemonCodeAnalyzer.getInstance(project).restart()
//                }
//            }
//        }
//
//        // Add the new listener
//        editor.selectionModel.addSelectionListener(selectionListener!!)
//
////        // Generate initial code visions
////        computeCodeVision(editor, Unit)
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
//                        elementType.contains("method") ||
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
//            return runReadAction {
//                val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
//                val project = editor.project ?: return@runReadAction READY_EMPTY
//                val controller = InlineChatController(editor, editor.project!!)
//                val document = editor.document
//                val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@runReadAction READY_EMPTY
//
//                val selectionModel = editor.selectionModel
//                val selectionStart = selectionModel.selectionStart
//                val selectionEnd = selectionModel.selectionEnd
//
//                // If there's no selection, return empty result
//                var ranges: List<TextRange> = emptyList()
//                if (selectionStart == selectionEnd) {
//                    val offset = editor.caretModel.offset
//                    val nearestFunction = PsiTreeUtil.findFirstParent(file.findElementAt(offset)) { element ->
//                        element.node.elementType.toString().toLowerCase().contains("function") ||
//                            element.node.elementType.toString().toLowerCase().contains("method")
//                    }
//
//                    if (nearestFunction != null) {
//                        val textRange = nearestFunction.textRange
//                        ranges = listOf(textRange)
//                    }
//                } else {
//                    val traverser = SyntaxTraverser.psiTraverser(file)
//                    ranges = traverser.preOrderDfsTraversal()
//                        .filter { element ->
//                            val elementType = element.elementType.toString().toLowerCase()
//                            element.textRange.intersects(selectionStart, selectionEnd) &&
//                                elementType.contains("function") ||
//                                elementType.contains("method") ||
//                                elementType.contains("class")
//                        }.map { element -> element.textRange }.toMutableList()
//                }
//
//                for (range in ranges) {
//                    val adjustedRange = TextRange(
//                        max(range.startOffset, selectionStart),
//                        min(range.endOffset, selectionEnd)
//                    )
//                    val clickHandler: (MouseEvent?, Editor) -> Unit = { e: MouseEvent?, _ ->
//                        controller.initPopup()
//                    }
//                    val text = if(controller.getIsInProgress() || controller.getShouldShowActions()) "Chat is working..." else "AmazonQ Chat"
//                    val entry = ClickableTextCodeVisionEntry(text, id, clickHandler, icon = null, text, text, emptyList())
//                    lenses.add(Pair(adjustedRange, entry))
//                }
//
//                return@runReadAction CodeVisionState.Ready(lenses)
//            }
    }
}
