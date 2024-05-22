// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.lang.Language
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue

fun formatText(project: Project, language: Language, content: String): String {
    var result = content
    CommandProcessor.getInstance().runUndoTransparentAction {
        PsiFileFactory.getInstance(project)
            .createFileFromText("foo.bar", language, content, false, true)?.let {
                result = CodeStyleManager.getInstance(project).reformat(it).text
            }
    }

    return result
}

fun extractChanges(issue: CodeWhispererCodeScanIssue): Pair<Int, List<String>> {
    val codeLines = issue.suggestedFixes.firstOrNull()?.code?.split("\n").orEmpty()
    val linesToDelete = codeLines.count { it.startsWith("-") }
    val linesToInsert = codeLines.mapNotNull { line ->
        if (line.startsWith("+")) line.removePrefix("+") else null
    }
    return linesToDelete to linesToInsert
}

fun updateEditorDocument(document: Document, issue: CodeWhispererCodeScanIssue, project: Project) {
    val (linesToDelete, linesToInsert) = extractChanges(issue)
    val startLineOffset = document.getLineStartOffset(issue.startLine - 1)
    val endLineOffset = document.getLineEndOffset(issue.startLine + linesToDelete - 2)
    document.replaceString(startLineOffset, endLineOffset, linesToInsert.joinToString("\n"))
    PsiDocumentManager.getInstance(project).commitDocument(document)
}

/**
 * Designed to convert underscore separated words (e.g. UPDATE_COMPLETE) into title cased human readable text
 * (e.g. Update Complete)
 */
fun String.toHumanReadable() = StringUtil.toTitleCase(toLowerCase().replace('_', ' '))
