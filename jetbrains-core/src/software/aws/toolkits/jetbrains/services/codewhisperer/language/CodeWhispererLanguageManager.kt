// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import com.intellij.openapi.components.service
import software.aws.toolkits.jetbrains.services.codewhisperer.model.ProgrammingLanguage
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererLanguageManager {
    fun isLanguageSupported(language: String): Boolean =
        language == CodewhispererLanguage.Java.toString() ||
            language == CodewhispererLanguage.Python.toString() ||
            language == CodewhispererLanguage.Javascript.toString()

    /**
     * This should only be called inside CodeWhispererService.buildCodeWhispererRequest.
     * e.g. JSX -> JavaScript, TypeScript -> JavaScript etc.
     */
    internal fun mapProgrammingLanguage(language: ProgrammingLanguage): ProgrammingLanguage =
        when {
            language.languageName.contains("jsx") -> ProgrammingLanguage(CodewhispererLanguage.Javascript)
            else -> language
        }

    companion object {
        fun getInstance(): CodeWhispererLanguageManager = service()
    }
}

fun ProgrammingLanguage.toCodeWhispererLanguage() = when (languageName) {
    CodewhispererLanguage.Python.toString() -> CodewhispererLanguage.Python
    CodewhispererLanguage.Java.toString() -> CodewhispererLanguage.Java
    CodewhispererLanguage.Javascript.toString() -> CodewhispererLanguage.Javascript
    "plain_text" -> CodewhispererLanguage.Plaintext
    else -> CodewhispererLanguage.Unknown
}

fun CodewhispererLanguage.toProgrammingLanguage() = ProgrammingLanguage(this.toString())
