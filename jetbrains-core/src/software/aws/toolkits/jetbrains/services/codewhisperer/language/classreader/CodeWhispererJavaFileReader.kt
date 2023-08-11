// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.classreader

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile

class CodeWhispererJavaFileReader : FileReader {
    override fun readClass(psiFile: PsiFile): Map<FileReaderKey, List<String>> {
        if (psiFile !is PsiClassOwner) {
            return emptyMap()
        }

        return runReadAction {
            val classNames = psiFile.classes.mapNotNull {
                it.name
            }

            val methodNames = psiFile.classes.mapNotNull {
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

    override fun readTopLevelFunc(psiFile: PsiFile): List<String> = emptyList()
}
