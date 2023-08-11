// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.codewhisperer.language.classresolver.CodeWhispererPythonClassResolver
import software.aws.toolkits.jetbrains.services.codewhisperer.language.classresolver.FileReader
import software.aws.toolkits.jetbrains.services.codewhisperer.language.classresolver.FileReaderKey
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererFileCrawler

class PythonCodeWhispererFileCrawler : CodeWhispererFileCrawler() {
    override val fileExtension: String = "py"
    override val dialects: Set<String> = setOf("py")
    override val testFileNamingPatterns: List<Regex> = listOf(
        Regex("""^test_(.+)(\.py)$"""),
        Regex("""^(.+)_test(\.py)$""")
    )

    override suspend fun listFilesImported(psiFile: PsiFile): List<VirtualFile> = emptyList()

    override fun findSourceFileByName(psiFile: PsiFile): VirtualFile? = super.listFilesUnderProjectRoot(psiFile.project).find {
        !it.isDirectory &&
            it.isWritable &&
            it.name != psiFile.virtualFile.name &&
            it.name == guessSourceFileName(psiFile.name)
    }

    /**
     * check files in editors and pick one which has most substring matches to the target
     */
    override fun findSourceFileByContent(psiFile: PsiFile): VirtualFile? = searchKeywordsInOpenedFile(psiFile) { myPsiFile ->
        FileReader.EP_NAME.findFirstSafe { it is CodeWhispererPythonClassResolver }?.let {
            val classAndMethods = it.readClass(myPsiFile)
            val func = it.readTopLevelFunc(myPsiFile)
            val clazz = classAndMethods[FileReaderKey.ClassName].orEmpty()
            val methods = classAndMethods[FileReaderKey.MethodName].orEmpty()

            clazz + methods + func
        }.orEmpty()
    }
}
