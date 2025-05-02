// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.languages

import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererRuby private constructor() : CodeWhispererProgrammingLanguage() {
    override val languageId: String = ID

    override fun toTelemetryType(): CodewhispererLanguage = CodewhispererLanguage.Ruby

    override fun isAutoFileScanSupported(): Boolean = true

    override fun lineCommentPrefix(): String = "#"

    override fun blockCommentPrefix(): String = "=begin"

    override fun blockCommentSuffix(): String = "=end"

    companion object {
        const val ID = "ruby"

        val INSTANCE = CodeWhispererRuby()
    }
}
