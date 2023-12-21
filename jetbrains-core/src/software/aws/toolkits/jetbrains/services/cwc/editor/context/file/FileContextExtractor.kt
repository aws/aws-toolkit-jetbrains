// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.file

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util.LanguageExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util.MatchPolicyExtractor
import software.aws.toolkits.jetbrains.services.cwc.utility.EdtUtility

class FileContextExtractor(
    private val fqnWebviewAdapter: FqnWebviewAdapter,
    private val project: Project,
    private val languageExtractor: LanguageExtractor = LanguageExtractor(),
) {
    suspend fun extract(): FileContext? {
        var noEditor = false
        var fileLanguage: String? = ""
        var fileText = ""
        var filePath: String? = ""

        EdtUtility.runInEdt {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor

            // detektMain prevents us from using !! requiring us to use this workaround
            if (editor == null) {
                noEditor = true
                return@runInEdt
            }

            fileLanguage = languageExtractor.extractLanguageNameFromCurrentFile(editor, project)
            fileText = editor.document.text

            val doc: Document = editor.document
            val psiFile: PsiFile? = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            filePath = psiFile?.virtualFile?.path
        }

        if (noEditor) {
            return null
        }

        val matchPolicy = MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(
            isCodeSelected = false,
            fileLanguage = fileLanguage,
            fileText = fileText,
            fqnWebviewAdapter,
        )

        return FileContext(
            fileLanguage = fileLanguage,
            filePath = filePath,
            matchPolicy = matchPolicy,
        )
    }
}
