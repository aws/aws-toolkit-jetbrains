// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.languages

import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererPowershell private constructor() : CodeWhispererProgrammingLanguage() {
    override val languageId: String = ID

    override fun toTelemetryType(): CodewhispererLanguage = CodewhispererLanguage.Powershell

    // TODO: enable it when service is ready
    override fun isCodeCompletionSupported(): Boolean = false

    companion object {
        // TODO: confirm with service team language id
        const val ID = "powershell"

        val INSTANCE = CodeWhispererPowershell()
    }
}