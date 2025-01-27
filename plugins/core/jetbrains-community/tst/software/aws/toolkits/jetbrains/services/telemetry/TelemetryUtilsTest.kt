// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class TelemetryUtilsTest {

    @ParameterizedTest
    @MethodSource("scrubNamesTestCases")
    fun testScrubNames(input: String, expected: String) {
        val fakeUser = "jdoe123"
        assertThat(expected).isEqualTo(scrubNames(input, fakeUser))
    }

    companion object {
        @JvmStatic
        fun scrubNamesTestCases(): Stream<Arguments> = Stream.of(
            Arguments.of("", ""),
            Arguments.of("a ./ b", "a ./ b"),
            Arguments.of("a ../ b", "a ../ b"),
            Arguments.of("a /.. b", "a /.. b"),
            Arguments.of("a //..// b", "a //..// b"),
            Arguments.of("a / b", "a / b"),
            Arguments.of("a ~/ b", "a ~/ b"),
            Arguments.of("a //// b", "a //// b"),
            Arguments.of("a .. b", "a .. b"),
            Arguments.of("a . b", "a . b"),
            Arguments.of("      lots      of         space       ", "lots of space"),
            Arguments.of(
                "Failed to save c:/fooß/aïböcß/aób∑c/∑ö/ππ¨p/ö/a/bar123öabc/baz.txt no permissions (error!)",
                "Failed to save c:/xß/xï/xó/x∑/xπ/xö/x/xö/x.txt no permissions (error!)"
            ),
            Arguments.of(
                "user: jdoe123 file: C:/Users/user1/.aws/sso/cache/abc123.json (regex: /foo/)",
                "user: x file: C:/Users/x/.aws/sso/cache/x.json (regex: /x/)"
            ),
            Arguments.of("/Users/user1/foo.jso", "/Users/x/x.jso"),
            Arguments.of("/Users/user1/foo.js", "/Users/x/x.js"),
            Arguments.of("/Users/user1/noFileExtension", "/Users/x/x"),
            Arguments.of("/Users/user1/minExtLength.a", "/Users/x/x.a"),
            Arguments.of("/Users/user1/extIsNum.123456", "/Users/x/x.123456"),
            Arguments.of("/Users/user1/foo.looooooooongextension", "/Users/x/x.looooooooongextension"),
            Arguments.of("/Users/user1/multipleExts.ext1.ext2.ext3", "/Users/x/x.ext3"),
            Arguments.of("c:\\fooß\\bar\\baz.txt", "c:/xß/x/x.txt"),
            Arguments.of("unc path: \\\\server$\\pipename\\etc END", "unc path: //x$/x/x END"),
            Arguments.of(
                "c:\\Users\\user1\\.aws\\sso\\cache\\abc123.json jdoe123 abc",
                "c:/Users/x/.aws/sso/cache/x.json x abc"
            ),
            Arguments.of("unix /home/jdoe123/.aws/config failed", "unix /home/x/.aws/config failed"),
            Arguments.of("unix ~jdoe123/.aws/config failed", "unix ~x/.aws/config failed"),
            Arguments.of("unix ../../.aws/config failed", "unix ../../.aws/config failed"),
            Arguments.of("unix ~/.aws/config failed", "unix ~/.aws/config failed"),
            Arguments.of(
                "/Users/user1/.aws/sso/cache/abc123.json no space",
                "/Users/x/.aws/sso/cache/x.json no space"
            )
        )
    }

    @Test
    fun `includes message for safe exceptions`() {
        val safeError = SafeError("Safe error message")
        val result = getStackTraceForError(safeError)

        assertThat(result).contains("Safe error message")
        assertThat(result).contains("SafeError: ")
        assertThat(result).contains("at software.aws.toolkits.jetbrains.services.telemetry.TelemetryUtilsTest")
    }

    @Test
    fun `excludes message for unsafe exceptions`() {
        val unsafeError = UnsafeError("Sensitive information")
        val result = getStackTraceForError(unsafeError)

        assertThat(result).doesNotContain("Sensitive information")
        assertThat(result).contains("UnsafeError")
        assertThat(result).contains("at software.aws.toolkits.jetbrains.services.telemetry.TelemetryUtilsTest")
    }

    @Test
    fun `respects recursion limit for nested exceptions`() {
        val recursionLimit = 3
        val exceptions = mutableListOf<Exception>()

        var currentError = SafeError("depth ${recursionLimit + 2}")
        exceptions.add(currentError)

        for (i in (recursionLimit + 1) downTo 0) {
            currentError = SafeError("depth $i", cause = currentError)
            exceptions.add(currentError)
        }

        val stackTrace = getStackTraceForError(exceptions.last())

        // Assert exceptions within limit are included
        for (i in 0 until recursionLimit) {
            assertThat(stackTrace).contains("depth $i")
        }

        // Assert exceptions beyond limit are not included
        for (i in recursionLimit..exceptions.size) {
            assertThat(stackTrace).doesNotContain("depth $i")
        }
    }

    @Test
    fun `test suppressed exceptions are included`() {
        val suppressedException = SafeError("Test 2")
        val mainException = SafeError("Test 1")
        mainException.addSuppressed(suppressedException)

        val stackTrace = getStackTraceForError(mainException)

        assertThat(stackTrace).contains("Suppressed: ")
        assertThat(stackTrace).contains("Test 1")
        assertThat(stackTrace).contains("Test 2")

        val substring = "at software.aws.toolkits.jetbrains.services.telemetry.TelemetryUtilsTest"
        assertThat(stackTrace.windowed(substring.length).count { it == substring }).isEqualTo(2)
    }

    private class SafeError(message: String, cause: Throwable? = null) :
        Exception(message, cause), SafeMessageError

    private class UnsafeError(message: String) : Exception(message)
}
