// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.classreader

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile

class CodeWhispererPythonReader : FileReader {
    override fun readClass(psiFile: PsiFile): Map<FileReaderKey, List<String>> {
        if (psiFile !is PyFile) {
            return emptyMap()
        }

        return runReadAction {
            val classNames = psiFile.topLevelClasses.mapNotNull {
                it.name
            }

            val methodNames = psiFile.topLevelClasses.mapNotNull {
                it.methods.mapNotNull { method ->
                    method.name
                }
            }.flatten()

            mapOf(
                FileReaderKey.ClassName to classNames,
                FileReaderKey.MethodName to methodNames
            )
        }
    }

    override fun readTopLevelFunc(psiFile: PsiFile): List<String> {
        if (psiFile !is PyFile) {
            return emptyList()
        }

        return runReadAction {
            psiFile.topLevelFunctions.mapNotNull {
                it.name
            }
        }
    }
}
