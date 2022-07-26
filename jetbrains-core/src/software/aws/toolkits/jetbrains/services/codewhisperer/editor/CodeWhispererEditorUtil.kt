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
import software.amazon.awssdk.utils.StringUtils.lowerCase
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.ProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.LEFT_CONTEXT_ON_CURRENT_LINE
import java.awt.Point
import kotlin.math.max
import kotlin.math.min

object CodeWhispererEditorUtil {
    /**
     * it's a delegate function which will call the following three specific function to get metadata instead of call 3 times for each one
     */
    fun getFileContextInfo(editor: Editor, psiFile: PsiFile): FileContextInfo {
        val caretContext = extractCaretContext(editor)
        val fileName = getFileName(psiFile)
        val programmingLanguage = ProgrammingLanguage(psiFile.toProgrammingLanguage())
        return FileContextInfo(caretContext, fileName, programmingLanguage)
    }
    private fun PsiFile.toProgrammingLanguage() = lowerCase(this.fileType.name)

    private fun extractCaretContext(editor: Editor): CaretContext {
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
                caretOffset, totalCharLength.coerceAtMost(CodeWhispererConstants.CHARACTERS_LIMIT + caretOffset)
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
