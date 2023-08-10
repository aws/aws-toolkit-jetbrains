// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererC
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCpp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCsharp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererGo
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererKotlin
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPhp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPlainText
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRuby
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRust
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererScala
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererShell
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererSql
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage

class CodeWhispererLanguageManager {
    // Always use this method to check for language support for CodeWhisperer features.
    // The return type here implicitly means that the corresponding language plugin has been installed to the user's IDE,
    // (e.g. 'Python' plugin for Python and 'JavaScript and TypeScript' for JS/TS). So we can leverage these language
    // plugin features when developing CodeWhisperer features.
    /**
     * resolve language by
     * 1. file type
     * 2. extension
     * 3. fallback to unknown
     */
    fun getLanguage(vFile: VirtualFile): CodeWhispererProgrammingLanguage {
        val fileTypeName = vFile.fileType.name.lowercase()

        val fileExtension = vFile.extension?.lowercase()

        // We want to support Python Console which does not have a file extension
        if (fileExtension == null && !fileTypeName.contains("python")) {
            return CodeWhispererUnknownLanguage.INSTANCE
        }

        val supportedLanguages = CodeWhispererProgrammingLanguage.EP_NAME.extensionList.associateBy { it.languageId }

        val languageId = when {
            fileTypeName.contains("python") || fileExtension == "py" -> CodeWhispererPython.ID
            fileTypeName.contains("javascript") || fileExtension == "js" -> CodeWhispererJavaScript.ID
            fileTypeName.contains("java") || fileExtension == "java" -> CodeWhispererJava.ID
            fileTypeName.contains("jsx harmony") || fileExtension == "jsx" -> CodeWhispererJsx.ID
            fileTypeName.contains("c#") || fileExtension == "cs" -> CodeWhispererCsharp.ID
            fileTypeName.contains("typescript jsx") || fileExtension == "tsx" -> CodeWhispererTsx.ID
            fileTypeName.contains("typescript") -> CodeWhispererTypeScript.ID
            fileTypeName.contains("scala") || fileExtension == "scala" -> CodeWhispererScala.ID
            fileTypeName.contains("kotlin") || fileExtension == "kt" -> CodeWhispererKotlin.ID
            fileTypeName.contains("ruby") || fileExtension == "rb" -> CodeWhispererRuby.ID
            fileTypeName.contains("php") || fileExtension == "php" -> CodeWhispererPhp.ID
            fileTypeName.contains("sql") || fileExtension == "sql" -> CodeWhispererSql.ID
            fileTypeName.contains("go") || fileExtension == "go" -> CodeWhispererGo.ID
            fileTypeName.contains("shell") || fileExtension == "sh" -> CodeWhispererShell.ID
            fileTypeName.contains("rust") || fileExtension == "rs" -> CodeWhispererRust.ID
            fileTypeName.contains("plain_text") || fileExtension == "txt" -> CodeWhispererPlainText.ID
            fileExtension == "c" || fileExtension == "h" -> CodeWhispererC.ID
            fileExtension == "cpp" || fileExtension == "c++" || fileExtension == "cc" -> CodeWhispererCpp.ID
            else -> null
        }

        return supportedLanguages[languageId] ?: CodeWhispererUnknownLanguage.INSTANCE
    }

    /**
     * will call getLanguage(virtualFile) first, then fallback to string resolve in case of psi only exists in memory
     */
    fun getLanguage(psiFile: PsiFile): CodeWhispererProgrammingLanguage = psiFile.virtualFile?.let {
        getLanguage(it)
    } ?: languageExtensionsMap.keys.find { ext -> psiFile.name.endsWith(ext) }?.let {
        val supportedLanguages = CodeWhispererProgrammingLanguage.EP_NAME.extensionList.associateBy { it.languageId }
        supportedLanguages[languageExtensionsMap[it]]
    } ?: CodeWhispererUnknownLanguage.INSTANCE

    companion object {
        fun getInstance(): CodeWhispererLanguageManager = service()

        /**
         * languageExtensionMap will look like
         * {
         *      "cpp" to CodeWhispererCpp.ID,
         *      "c++" to CodeWhispererCpp.ID,
         *      "cc" to CodeWhispererCpp.ID,
         *      "java" to CodeWhispererJava.ID,
         *      ...
         * }
         */
        val languageExtensionsMap = listOf(
            listOf("java") to CodeWhispererJava.ID,
            listOf("py") to CodeWhispererPython.ID,
            listOf("js") to CodeWhispererJavaScript.ID,
            listOf("jsx") to CodeWhispererJsx.ID,
            listOf("ts") to CodeWhispererTypeScript.ID,
            listOf("tsx") to CodeWhispererTsx.ID,
            listOf("cs") to CodeWhispererCsharp.ID,
            listOf("kt") to CodeWhispererKotlin.ID,
            listOf("scala") to CodeWhispererScala.ID,
            listOf("c", "h") to CodeWhispererC.ID,
            listOf("cpp", "c++", "cc") to CodeWhispererCpp.ID,
            listOf("sh") to CodeWhispererShell.ID,
            listOf("rb") to CodeWhispererRuby.ID,
            listOf("rs") to CodeWhispererRust.ID,
            listOf("go") to CodeWhispererGo.ID,
            listOf("php") to CodeWhispererPhp.ID,
            listOf("sql") to CodeWhispererSql.ID,
            listOf("txt") to CodeWhispererPlainText.ID
        ).map {
            val exts = it.first
            val lang = it.second
            exts.map { ext -> ext to lang }
        }.flatten()
            .associateBy({ it.first }, { it.second })
    }
}

fun PsiFile.programmingLanguage(): CodeWhispererProgrammingLanguage = CodeWhispererLanguageManager.getInstance().getLanguage(this)

fun VirtualFile.programmingLanguage(): CodeWhispererProgrammingLanguage = CodeWhispererLanguageManager.getInstance().getLanguage(this)
