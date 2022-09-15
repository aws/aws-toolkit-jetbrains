// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import software.aws.toolkits.telemetry.CodewhispererLanguage

abstract class CodeWhispererProgrammingLanguage {
    abstract val languageId: String

    abstract fun toTelemetryType(): CodewhispererLanguage

    open fun isCodeCompletionSupported(): Boolean = false

    open fun isSecurityScanSupported(): Boolean = false

    open fun toCodeWhispererRuntimeLanguage(): CodeWhispererProgrammingLanguage = this

    override fun equals(other: Any?): Boolean {
        if (other !is CodeWhispererProgrammingLanguage) return false
        return this.languageId == other.languageId
    }

    /**
     * we want to force CodeWhispererProgrammingLanguage(any language implement it) be singleton,
     * override hashCode is the backup plan if another object is being created
     */
    override fun hashCode(): Int = this.languageId.hashCode()
}
