// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo

/**
 * [extractFileContext] will extract the context from a psi file provided
 */
interface FileContextProvider {
    fun extractFileContext(editor: Editor, psiFile: PsiFile): FileContextInfo

    companion object {
        fun getInstance(project: Project): FileContextProvider = project.service()
    }
}

class DefaultCodeWhispererFileContextProvider : FileContextProvider {
    override fun extractFileContext(editor: Editor, psiFile: PsiFile): FileContextInfo = CodeWhispererEditorUtil.getFileContextInfo(editor, psiFile)
}
