// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GlibcVersionTest {
    @Test
    fun `parse reads a bare major minor string`() {
        assertThat(GlibcVersion.parse("2.28")).isEqualTo(GlibcVersion(2, 28))
    }

    @Test
    fun `parse reads getconf style output`() {
        assertThat(GlibcVersion.parse("glibc 2.26")).isEqualTo(GlibcVersion(2, 26))
    }

    @Test
    fun `parse reads ldd style output with trailing text`() {
        val ldd = """
            ldd (GNU libc) 2.31
            Copyright (C) 2020 Free Software Foundation, Inc.
        """.trimIndent()

        assertThat(GlibcVersion.parse(ldd)).isEqualTo(GlibcVersion(2, 31))
    }

    @Test
    fun `parse returns null for null blank and non-version input`() {
        assertThat(GlibcVersion.parse(null)).isNull()
        assertThat(GlibcVersion.parse("")).isNull()
        assertThat(GlibcVersion.parse("   ")).isNull()
        assertThat(GlibcVersion.parse("musl libc")).isNull()
    }

    @Test
    fun `compareTo orders by major then minor numerically`() {
        assertThat(GlibcVersion(2, 26)).isLessThan(GlibcVersion(2, 28))
        assertThat(GlibcVersion(2, 31)).isGreaterThan(GlibcVersion(2, 28))
        assertThat(GlibcVersion(3, 0)).isGreaterThan(GlibcVersion(2, 99))
        assertThat(GlibcVersion(2, 28)).isEqualByComparingTo(GlibcVersion(2, 28))
    }

    @Test
    fun `compareTo is numeric not lexicographic`() {
        // A string compare would rank "2.9" above "2.10"; the numeric compare must not.
        assertThat(GlibcVersion(2, 9)).isLessThan(GlibcVersion(2, 10))
    }

    @Test
    fun `required floor is glibc 2 28`() {
        assertThat(GlibcVersion.REQUIRED).isEqualTo(GlibcVersion(2, 28))
    }

    @Test
    fun `toString renders major minor`() {
        assertThat(GlibcVersion(2, 28).toString()).isEqualTo("2.28")
    }
}
