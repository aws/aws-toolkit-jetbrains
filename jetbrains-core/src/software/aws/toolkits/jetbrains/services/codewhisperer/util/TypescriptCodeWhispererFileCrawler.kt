// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object TypescriptCodeWhispererFileCrawler : CodeWhispererFileCrawler() {
    override val fileExtension: String = "ts"
    override val dialects: Set<String> = setOf("ts", "tsx")
    override val testFilePatterns: List<Regex> = listOf(
        Regex("""^(.+)\.test\.(?:ts|tsx)\$"""),
        Regex("""^(.+)\.spec\.(?:ts|tsx)\$""")
    )

    override fun findFocalFileForTest(psiFile: PsiFile): VirtualFile? = null

    override fun findSourceFileByName(psiFile: PsiFile): VirtualFile? = null

    override fun findSourceFileByContent(psiFile: PsiFile): VirtualFile? = null
}
