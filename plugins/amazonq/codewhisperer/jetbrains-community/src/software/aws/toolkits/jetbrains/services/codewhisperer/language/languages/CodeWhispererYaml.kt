// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language.languages

import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererYaml private constructor() : CodeWhispererProgrammingLanguage() {
    override val languageId: String = ID

    override fun toTelemetryType(): CodewhispererLanguage = CodewhispererLanguage.Yaml

    override fun isAutoFileScanSupported(): Boolean = true

    override fun lineCommentPrefix(): String = "#"

    override fun blockCommentPrefix(): String? = null

    override fun blockCommentSuffix(): String? = null

    companion object {
        const val ID = "yaml"

        val INSTANCE = CodeWhispererYaml()
    }
}
