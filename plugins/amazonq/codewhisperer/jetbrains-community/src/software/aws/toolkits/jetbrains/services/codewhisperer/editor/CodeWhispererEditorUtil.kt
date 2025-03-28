// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.editor

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.ui.popup.AbstractPopup
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.AWSTemplateCaseInsensitiveKeyWordsRegex
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.AWSTemplateKeyWordsRegex
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.JsonConfigFileNamingConvention
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.LEFT_CONTEXT_ON_CURRENT_LINE
import java.awt.Point
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer

import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Document
import com.intellij.lang.annotation.HighlightSeverity

fun getHighlightedProblems(document: Document, project: Project): List<ProblemInfo> {
    val markupModel = DocumentMarkupModel.forDocument(document, project, true)
    val highlighters = markupModel.getAllHighlighters()
    return highlighters
        .filter { highlighter ->
            // Filter only error and warning highlighters
//            val severity = highlighter.errorStripeTooltip
//                ?.let { if (it is String) null else it }
//                ?.let { (it as? com.intellij.lang.annotation.AnnotationHolder)?.severity }
//                ?: HighlightSeverity.INFORMATION
            val severity = HighlightSeverity.INFORMATION
            severity == HighlightSeverity.ERROR || severity == HighlightSeverity.WARNING
        }
        .map { highlighter ->
            ProblemInfo(
                startOffset = highlighter.startOffset,
                endOffset = highlighter.endOffset,
                message = getHighlighterMessage(highlighter),
                severity = getHighlighterSeverity(highlighter)
            )
        }
}

private fun getHighlighterMessage(highlighter: RangeHighlighter): String {
    return when (val tooltip = highlighter.errorStripeTooltip) {
        is String -> tooltip
        is com.intellij.lang.annotation.AnnotationHolder -> tooltip.toString()
        else -> tooltip?.toString() ?: ""
    }
}

private fun getHighlighterSeverity(highlighter: RangeHighlighter): HighlightSeverity {
    return HighlightSeverity.INFORMATION
//    return highlighter.errorStripeTooltip
//        ?.let { if (it is String) null else it }
//        ?.let { (it as? com.intellij.lang.annotation.AnnotationHolder)?.severity }
//        ?: HighlightSeverity.INFORMATION
}

// Data class to hold problem information
data class ProblemInfo(
    val startOffset: Int,
    val endOffset: Int,
    val message: String,
    val severity: HighlightSeverity
)

// Example usage:
fun example(document: Document, project: Project) {
    val problems = getHighlightedProblems(document, project)

    problems.forEach { problem ->
        println("""
            Problem found:
            - Message: ${problem.message}
            - Severity: ${problem.severity}
            - Range: ${problem.startOffset}..${problem.endOffset}
        """.trimIndent())
    }
}

object CodeWhispererEditorUtil {
    fun getFileContextInfo(editor: Editor, psiFile: PsiFile): FileContextInfo {
        val caretContext = extractCaretContext(editor)
        val fileName = getFileName(psiFile)
        val programmingLanguage = psiFile.programmingLanguage()
        val fileRelativePath = getRelativePathToContentRoot(editor)

        val pp = ProblemsCollector.getInstance(editor.project!!).getFileProblems(editor.virtualFile);

        val problemsView = ProblemsView.getToolWindow(editor.project!!)

        val problems = getHighlightedProblems(editor.document, editor.project!!)
        return FileContextInfo(caretContext, fileName, programmingLanguage, fileRelativePath)
    }

    fun extractCaretContext(editor: Editor): CaretContext {
        val document = editor.document
        val caretOffset = editor.caretModel.primaryCaret.offset
        val totalCharLength = editor.document.textLength

        val caretLeftFileContext = document.getText(
            TextRange(
                CodeWhispererConstants.BEGINNING_OF_FILE.coerceAtLeast(caretOffset - CodeWhispererConstants.CHARACTERS_LIMIT),
                caretOffset
            )
        )

        val caretRightFileContext = document.getText(
            TextRange(
                caretOffset,
                totalCharLength.coerceAtMost(CodeWhispererConstants.CHARACTERS_LIMIT + caretOffset)
            )
        )

        val lineNumber = document.getLineNumber(caretOffset)
        val startOffset = document.getLineStartOffset(lineNumber)
        var leftContextOnCurrentLine = document.getText(TextRange(startOffset, caretOffset))
        leftContextOnCurrentLine = leftContextOnCurrentLine.substring(
            leftContextOnCurrentLine.length - leftContextOnCurrentLine.length.coerceAtMost(LEFT_CONTEXT_ON_CURRENT_LINE)
        )

        return CaretContext(caretLeftFileContext, caretRightFileContext, leftContextOnCurrentLine)
    }

    fun getCaretPosition(editor: Editor): CaretPosition {
        val offset = editor.caretModel.primaryCaret.offset
        val line = editor.caretModel.primaryCaret.visualPosition.line
        return CaretPosition(offset, line)
    }

    private fun getFileName(psiFile: PsiFile): String =
        psiFile.name.substring(0, psiFile.name.length.coerceAtMost(CodeWhispererConstants.FILENAME_CHARS_LIMIT))

    fun getRelativePathToContentRoot(editor: Editor): String? =
        editor.project?.let { project ->
            FileDocumentManager.getInstance().getFile(editor.document)?.let { vFile ->
                val fileIndex = ProjectFileIndex.getInstance(project)
                val contentRoot = runReadAction { fileIndex.getContentRootForFile(vFile) }
                contentRoot?.let {
                    VfsUtilCore.getRelativePath(vFile, it)
                }
            }
        }

    fun getPopupPositionAboveText(editor: Editor, popup: JBPopup, offset: Int): Point {
        val textAbsolutePosition = editor.offsetToXY(offset)
        val editorLocation = editor.component.locationOnScreen
        val editorContentLocation = editor.contentComponent.locationOnScreen
        return Point(
            editorContentLocation.x + textAbsolutePosition.x,
            editorLocation.y + textAbsolutePosition.y - editor.scrollingModel.verticalScrollOffset -
                (popup as AbstractPopup).preferredContentSize.height
        )
    }

    fun shouldSkipInvokingBasedOnRightContext(editor: Editor): Boolean {
        val caretContext = runReadAction { extractCaretContext(editor) }
        val rightContextLines = caretContext.rightFileContext.split(Regex("\r?\n"))
        val rightContextCurrentLine = if (rightContextLines.isEmpty()) "" else rightContextLines[0]

        return rightContextCurrentLine.isNotEmpty() &&
            !rightContextCurrentLine.startsWith(" ") &&
            rightContextCurrentLine.trim() != ("}") &&
            rightContextCurrentLine.trim() != (")")
    }

    /**
     * Check if left context contains keywords or file name follow config json file naming pattern
     */
    fun isSupportedJsonFormat(fileName: String, leftContext: String): Boolean =
        JsonConfigFileNamingConvention.contains(fileName.lowercase()) ||
            AWSTemplateKeyWordsRegex.containsMatchIn(leftContext) ||
            AWSTemplateCaseInsensitiveKeyWordsRegex.containsMatchIn(leftContext.lowercase(Locale.getDefault()))

    /**
     * Checks if the [otherRange] overlaps this TextRange. Note that the comparison is `<` because the endOffset of TextRange is exclusive.
     */
    fun TextRange.overlaps(otherRange: TextRange): Boolean =
        if (otherRange.isEmpty) {
            // Handle case when otherRange is empty and within the range
            otherRange.startOffset in startOffset until endOffset
        } else {
            max(startOffset, otherRange.startOffset) < min(endOffset, otherRange.endOffset)
        }
}
