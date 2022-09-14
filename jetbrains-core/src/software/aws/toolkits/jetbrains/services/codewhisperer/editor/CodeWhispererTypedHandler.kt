// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.telemetry.CodewhispererAutomatedTriggerType
import software.aws.toolkits.telemetry.CodewhispererTriggerType

class CodeWhispererTypedHandler : TypedHandlerDelegate(), CodeWhispererAutoTriggerHandler {
    private var triggerOnIdle: Job? = null
    override fun charTyped(c: Char, project: Project, editor: Editor, psiFiles: PsiFile): Result {
        triggerOnIdle?.cancel()
        if (!CodeWhispererService.getInstance().canDoInvocation(editor, CodewhispererTriggerType.AutoTrigger)) {
            return Result.CONTINUE
        }

        triggerOnIdle = projectCoroutineScope(project).launch {
            while (!CodeWhispererInvocationStatus.getInstance().hasEnoughDelayToInvokeCodeWhisperer()) {
                if (!isActive) return@launch
                delay(CodeWhispererConstants.POPUP_DELAY_CHECK_INTERVAL)
            }
            runInEdt {
                if (CodeWhispererInvocationStatus.getInstance().isPopupActive()) return@runInEdt
                performAutomatedTriggerAction(editor, CodewhispererAutomatedTriggerType.KeyStrokeCount)
            }
        }

        return Result.CONTINUE
    }
}
