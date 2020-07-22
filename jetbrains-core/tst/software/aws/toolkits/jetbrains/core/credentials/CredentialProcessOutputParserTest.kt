// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Test
import java.time.Instant

class CredentialProcessOutputParserTest {
    private val sut = DefaultCredentialProcessOutputParser

    @Test
    fun `can parse a basic input`() = runTest(
        """{"Version":1, "AccessKeyId":"foo", "SecretAccessKey":"bar"}""",
        CredentialProcessOutput("foo", "bar", null, null)
    )

    @Test
    fun `can parse a basic input with expiration`() = runTest(
        """{"Version":1, "AccessKeyId":"foo", "SecretAccessKey":"bar", "Expiration":"1970-01-01T00:00:00Z"}""",
        CredentialProcessOutput("foo", "bar", null, Instant.EPOCH)
    )

    @Test
    fun `can parse a basic input with session`() = runTest(
        """{"Version":1, "AccessKeyId":"foo", "SecretAccessKey":"bar", "SessionToken":"session"}""",
        CredentialProcessOutput("foo", "bar", "session", null)
    )

    private fun runTest(@Language("JSON") input: String, expected: CredentialProcessOutput) {
        assertThat(sut.parse(input)).isEqualTo(expected)
    }
}
