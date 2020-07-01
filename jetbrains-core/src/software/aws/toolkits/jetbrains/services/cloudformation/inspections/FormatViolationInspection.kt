// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParser
import software.aws.toolkits.resources.message

abstract class FormatViolationInspection : LocalInspectionTool() {
    override fun runForWholeFile(): Boolean = true

    abstract fun isCloudFormationFile(file: PsiFile): Boolean

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!isCloudFormationFile(file)) {
            return null
        }

        val parsed = CfnParser.parse(file)
        val problems = parsed.formatProblems.map {
            manager.createProblemDescriptor(
                it.element,
                it.description,
                isOnTheFly,
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }

        return problems.toTypedArray()
    }

    override fun getStaticDescription(): String? = message("cloudformation.inspections.format_violation.description")
}
