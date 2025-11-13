// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage

class LanguageExtractor {
    fun extractLanguageNameFromCurrentFile(editor: Editor): String =
        runReadAction {
            editor.virtualFile?.programmingLanguage()?.languageId ?: "plaintext"
        }
}
