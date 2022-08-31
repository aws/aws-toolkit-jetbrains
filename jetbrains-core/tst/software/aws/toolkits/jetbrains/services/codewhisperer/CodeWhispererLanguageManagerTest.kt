// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.language.toCodeWhispererLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.toProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.ProgrammingLanguage
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererLanguageManagerTest {
    @Test
    fun `test toCodeWhispererLanguage`() {
        var codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Java).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Java)

        codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Python).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Python)

        codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Javascript).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Javascript)

        codewhispererLanguage = ProgrammingLanguage("plain_text").toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Plaintext)

        codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Unknown).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Unknown)

        codewhispererLanguage = ProgrammingLanguage("unknown language name").toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Unknown)
    }

    @Test
    fun `test toProgrammingLanguage`() {
        var programmingLanguage = CodewhispererLanguage.Python.toProgrammingLanguage()
        assertThat(programmingLanguage.languageName).isEqualTo(CodewhispererLanguage.Python.toString())

        programmingLanguage = CodewhispererLanguage.Java.toProgrammingLanguage()
        assertThat(programmingLanguage.languageName).isEqualTo(CodewhispererLanguage.Java.toString())

        programmingLanguage = CodewhispererLanguage.Javascript.toProgrammingLanguage()
        assertThat(programmingLanguage.languageName).isEqualTo(CodewhispererLanguage.Javascript.toString())
    }
}
