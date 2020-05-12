// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 *
 * Process JSON and YAML files by an external annotation tool ("cfn-lint").
 * External annotators are expected to be (relatively) slow and are started
 * after regular annotators have completed their work.
 *
 * Annotators work in three steps:
 * 1. [.collectInformation] is called to collect some data about a file needed for launching a tool
 * 2. collected data is passed to [.doAnnotate] which executes a tool and collect highlighting data
 * 3. highlighting data is applied to a file by [.apply]
 *
 */
class CloudFormationLintAnnotator : ExternalAnnotator<InitialAnnotationResults, List<CloudFormationLintAnnotation>>() {

    override fun collectInformation(psiFile: PsiFile) = InitialAnnotationResults(psiFile)

    override fun doAnnotate(initialAnnotationResults: InitialAnnotationResults) = Linter.execute(initialAnnotationResults)

    override fun apply(
        file: PsiFile,
        annotationResult: List<CloudFormationLintAnnotation>,
        holder: AnnotationHolder
    ) {
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)
        annotationResult.forEach { error ->
            val startOffset = error.location?.start?.let {
                document?.getLineStartOffset(it.lineNumber - 1)?.plus(it.columnNumber - 1)
            } ?: 0
            val endOffset = error.location?.end?.let {
                document?.getLineStartOffset(it.lineNumber - 1)?.plus(it.columnNumber - 1)
            } ?: 0
            val textRange = TextRange(startOffset, endOffset)
            holder.createAnnotation(error.severity, textRange, error.message)
        }
    }
}
