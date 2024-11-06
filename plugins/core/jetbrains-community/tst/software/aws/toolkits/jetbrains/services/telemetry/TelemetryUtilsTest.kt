// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetryUtilsTest {

    @Test
    fun testScrubNames() {
        val fakeUser = "jdoe123"

        assertEquals("", scrubNames("", fakeUser))
        assertEquals("a ./ b", scrubNames("a ./ b", fakeUser))
        assertEquals("a ../ b", scrubNames("a ../ b", fakeUser))
        assertEquals("a /.. b", scrubNames("a /.. b", fakeUser))
        assertEquals("a //..// b", scrubNames("a //..// b", fakeUser))
        assertEquals("a / b", scrubNames("a / b", fakeUser))
        assertEquals("a ~/ b", scrubNames("a ~/ b", fakeUser))
        assertEquals("a //// b", scrubNames("a //// b", fakeUser))
        assertEquals("a .. b", scrubNames("a .. b", fakeUser))
        assertEquals("a . b", scrubNames("a . b", fakeUser))
        assertEquals("lots of x", scrubNames("      lots      of         space       ", "space"))
        assertEquals(
            "Failed to save c:/xß/xï/xó/x∑/xπ/xö/x/xö/x.txt no permissions (error!)",
            scrubNames(
                "Failed to save c:/fooß/aïböcß/aób∑c/∑ö/ππ¨p/ö/a/bar123öabc/baz.txt no permissions (error!)",
                fakeUser
            )
        )
        assertEquals(
            "user: x file: C:/Users/x/.aws/sso/cache/x.json (regex: /x/)",
            scrubNames("user: jdoe123 file: C:/Users/user1/.aws/sso/cache/abc123.json (regex: /foo/)", fakeUser)
        )
        assertEquals("/Users/x/x.jso", scrubNames("/Users/user1/foo.jso", fakeUser))
        assertEquals("/Users/x/x.js", scrubNames("/Users/user1/foo.js", fakeUser))
        assertEquals("/Users/x/x", scrubNames("/Users/user1/noFileExtension", fakeUser))
        assertEquals("/Users/x/x.a", scrubNames("/Users/user1/minExtLength.a", fakeUser))
        assertEquals("/Users/x/x.123456", scrubNames("/Users/user1/extIsNum.123456", fakeUser))
        assertEquals(
            "/Users/x/x.looooooooongextension",
            scrubNames("/Users/user1/foo.looooooooongextension", fakeUser)
        )
        assertEquals("/Users/x/x.ext3", scrubNames("/Users/user1/multipleExts.ext1.ext2.ext3", fakeUser))

        assertEquals("c:/xß/x/x.txt", scrubNames("c:\\fooß\\bar\\baz.txt", fakeUser))
        //     assertEquals(
        //          "uhh c:/x x/ spaces /x hmm...",
        //          scrubNames("uhh c:\\path with\\ spaces \\baz.. hmm...", fakeUser)
        //      )
        assertEquals(
            "unc path: //x$/x/x END",
            scrubNames("unc path: \\\\server$\\pipename\\etc END", fakeUser)
        )
        assertEquals(
            "c:/Users/x/.aws/sso/cache/x.json x abc",
            scrubNames("c:\\Users\\user1\\.aws\\sso\\cache\\abc123.json jdoe123 abc", fakeUser)
        )
        assertEquals(
            "unix /home/x/.aws/config failed",
            scrubNames("unix /home/jdoe123/.aws/config failed", fakeUser)
        )
        assertEquals(
            "unix ~x/.aws/config failed",
            scrubNames("unix ~jdoe123/.aws/config failed", fakeUser)
        )
        assertEquals(
            "unix ../../.aws/config failed",
            scrubNames("unix ../../.aws/config failed", fakeUser)
        )
        assertEquals("unix ~/.aws/config failed", scrubNames("unix ~/.aws/config failed", fakeUser))
        assertEquals(
            "/Users/x/.aws/sso/cache/x.json no space",
            scrubNames("/Users/user1/.aws/sso/cache/abc123.json no space", fakeUser)
        )
    }
}
