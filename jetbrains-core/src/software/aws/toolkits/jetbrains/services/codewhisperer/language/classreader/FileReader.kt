// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.classreader

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

interface FileReader {
    fun readClass(psiFile: PsiFile): Map<FileReaderKey, List<String>>

    fun readTopLevelFunc(psiFile: PsiFile): List<String>

    companion object {
        val EP_NAME = ExtensionPointName<FileReader>("aws.toolkit.codewhisperer.fileReader")
    }
}

enum class FileReaderKey {
    ClassName,
    MethodName
}
