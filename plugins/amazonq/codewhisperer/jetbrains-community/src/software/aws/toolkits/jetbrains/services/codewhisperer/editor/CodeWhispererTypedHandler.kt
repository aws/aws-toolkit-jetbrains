// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants

class CodeWhispererTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, psiFiles: PsiFile): Result {
        // Special Char
        if (CodeWhispererConstants.SPECIAL_CHARACTERS_LIST.contains(c.toString())) {
            CodeWhispererAutoTriggerService.getInstance().invoke(editor, CodeWhispererAutomatedTriggerType.SpecialChar(c))
            return Result.CONTINUE
        }

        CodeWhispererAutoTriggerService.getInstance().invoke(editor, CodeWhispererAutomatedTriggerType.Classifier())

        return Result.CONTINUE
    }
}
