// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class LegacyLinuxDetectorTest {

    @Test
    fun `parseGlibcxxVersions extracts versions from strings output`() {
        val output = """
            GLIBCXX_3.4
            GLIBCXX_3.4.1
            GLIBCXX_3.4.29
            GLIBCXX_3.4.30
            some other text
        """.trimIndent()

        val detector = LegacyLinuxDetector()
        val versions = detector.parseGlibcxxVersions(output)

        assertThat(versions).containsExactlyInAnyOrder(
            listOf(3, 4),
            listOf(3, 4, 1),
            listOf(3, 4, 29),
            listOf(3, 4, 30)
        )
    }

    @Test
    fun `parseGlibcxxVersions returns empty for no matches`() {
        val detector = LegacyLinuxDetector()
        val versions = detector.parseGlibcxxVersions("no versions here")

        assertThat(versions).isEmpty()
    }

    @Test
    fun `compareVersions returns negative when first is less`() {
        assertThat(LegacyLinuxDetector.compareVersions(listOf(3, 4, 28), listOf(3, 4, 29))).isNegative()
    }

    @Test
    fun `compareVersions returns positive when first is greater`() {
        assertThat(LegacyLinuxDetector.compareVersions(listOf(3, 4, 30), listOf(3, 4, 29))).isPositive()
    }

    @Test
    fun `compareVersions returns zero when equal`() {
        assertThat(LegacyLinuxDetector.compareVersions(listOf(3, 4, 29), listOf(3, 4, 29))).isZero()
    }

    @Test
    fun `compareVersions handles different length versions`() {
        assertThat(LegacyLinuxDetector.compareVersions(listOf(3, 4), listOf(3, 4, 0))).isZero()
        assertThat(LegacyLinuxDetector.compareVersions(listOf(3, 4), listOf(3, 4, 1))).isNegative()
    }
}
