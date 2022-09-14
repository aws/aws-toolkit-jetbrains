// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.telemetry.CodewhispererAutomatedTriggerType
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class CodeWhispererTypedHandler : TypedHandlerDelegate(), CodeWhispererAutoTriggerHandler {
    private val idleTriggerTimer = Timer("Idle time auto tigger timer", false)
    private var triggerOnIdle: TimerTask? = null
    override fun charTyped(c: Char, project: Project, editor: Editor, psiFiles: PsiFile): Result {

        triggerOnIdle?.cancel()
        if (!CodeWhispererService.getInstance().canDoInvocation(editor, CodewhispererTriggerType.AutoTrigger)) {
            return Result.CONTINUE
        }
        if (CodeWhispererConstants.SPECIAL_CHARACTERS_LIST.contains(c.toString())) {
            performAutomatedTriggerAction(editor, CodewhispererAutomatedTriggerType.SpecialCharacters)
            return Result.CONTINUE
        }
        val invocationStatus = CodeWhispererInvocationStatus.getInstance()
        if (invocationStatus.checkKeyStrokeCountMeetThreshold()) {
            performAutomatedTriggerAction(editor, CodewhispererAutomatedTriggerType.KeyStrokeCount)
        } else {
            invocationStatus.incrementKeyStrokeCount()
        }

        triggerOnIdle = object: TimerTask() {
                override fun run() {
                    runReadAction { 
                        performAutomatedTriggerAction(editor, CodewhispererAutomatedTriggerType.KeyStrokeCount)
                    }
                }
        }
        idleTriggerTimer.schedule(triggerOnIdle, CodeWhispererConstants.IDLE_TIME_TRIGGER_THRESHOLD)

        return Result.CONTINUE
    }
}
