// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.services.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import software.aws.toolkit.jetbrains.services.telemetry.scrubNames
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
}
