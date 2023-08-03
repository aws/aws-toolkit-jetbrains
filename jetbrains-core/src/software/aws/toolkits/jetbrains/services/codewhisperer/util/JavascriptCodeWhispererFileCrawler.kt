// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object JavascriptCodeWhispererFileCrawler : CodeWhispererFileCrawler() {
    override val fileExtension: String = "js"
    override val dialects: Set<String> = setOf("js", "jsx")
    override val testFileNamingPatterns: List<Regex> = listOf(
        Regex("""^(.+)\.test\.(?:js|jsx)\$"""),
        Regex("""^(.+)\.spec\.(?:js|jsx)\$""")
    )

    override suspend fun listFilesImported(psiFile: PsiFile): List<VirtualFile> = emptyList()

    override fun findSourceFileByName(psiFile: PsiFile): VirtualFile? = null

    override fun findSourceFileByContent(psiFile: PsiFile): VirtualFile? = null
}
