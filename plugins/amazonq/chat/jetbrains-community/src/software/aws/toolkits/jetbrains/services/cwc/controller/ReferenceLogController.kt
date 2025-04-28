// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.controller

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.amazon.awssdk.services.codewhispererruntime.model.Span
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionReference
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionReferencePosition
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference

object ReferenceLogController {
    fun addReferenceLog(originalCode: String, codeReferences: List<CodeReference>?, editor: Editor, project: Project, inlineChatStartPosition: CaretPosition?) {
        // TODO flare: hook /dev references with flare correctly, this is only a compile error fix which is not tested
        codeReferences?.let { references ->
            val cwReferences = references.map { reference ->
                InlineCompletionReference(
                    referenceName = reference.repository ?: "",
                    referenceUrl = reference.url ?: "",
                    licenseName = reference.licenseName ?: "",
                    position = InlineCompletionReferencePosition(
                        startCharacter = reference.recommendationContentSpan?.start ?: 0,
                        endCharacter = reference.recommendationContentSpan?.end ?: 0,
                    )
                )
            }
            val manager = CodeWhispererCodeReferenceManager.getInstance(project)

            manager.insertCodeReference(
                originalCode,
                cwReferences,
                editor,
                inlineChatStartPosition ?: CodeWhispererEditorUtil.getCaretPosition(editor),
                null,
            )
        }
    }

    fun addReferenceLog(codeReferences: List<CodeReferenceGenerated>?, project: Project) {
        val manager = CodeWhispererCodeReferenceManager.getInstance(project)

        // TODO flare: hook /dev references with flare correctly, this is only a compile error fix which is not tested
        codeReferences?.forEach { reference ->
            val cwReferences = InlineCompletionReference(
                referenceName = reference.repository ?: "",
                referenceUrl = reference.url ?: "",
                licenseName = reference.licenseName ?: "",
                position = InlineCompletionReferencePosition(
                    startCharacter = reference.recommendationContentSpan?.start ?: 0,
                    endCharacter = reference.recommendationContentSpan?.end ?: 0,
                )
            )
            manager.addReferenceLogPanelEntry(reference = cwReferences, null, null, null)
        }
    }
}
