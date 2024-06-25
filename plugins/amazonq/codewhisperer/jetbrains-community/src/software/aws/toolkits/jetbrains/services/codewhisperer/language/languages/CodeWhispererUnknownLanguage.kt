// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.languages

import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererUnknownLanguage private constructor() : CodeWhispererProgrammingLanguage() {
    override val languageId: String = ID

    override fun toTelemetryType(): CodewhispererLanguage = CodewhispererLanguage.Unknown

    override fun toCodeWhispererRuntimeLanguage(): CodeWhispererProgrammingLanguage = CodeWhispererPlainText.INSTANCE

    companion object {
        const val ID = "unknown"

        val INSTANCE = CodeWhispererUnknownLanguage()
    }
}
