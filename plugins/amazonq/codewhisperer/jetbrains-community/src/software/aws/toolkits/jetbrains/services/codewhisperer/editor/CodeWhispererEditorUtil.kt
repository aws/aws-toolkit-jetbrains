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

object CodeWhispererEditorUtil {
    fun getFileContextInfo(editor: Editor, psiFile: PsiFile): FileContextInfo {
        val caretContext = extractCaretContext(editor)
        val fileName = getFileName(psiFile)
        val programmingLanguage = psiFile.programmingLanguage()
        val fileRelativePath = getRelativePathToContentRoot(editor)
        val fileUri = getFileUri(psiFile)
        return FileContextInfo(caretContext, fileName, programmingLanguage, fileRelativePath, fileUri)
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

    private fun getFileUri(psiFile: PsiFile): String? =
        psiFile.virtualFile?.takeIf { it.isValid }?.let { vFile ->
            vFile.url.substring(0, vFile.url.length.coerceAtMost(CodeWhispererConstants.FILENAME_CHARS_LIMIT))
        }

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
