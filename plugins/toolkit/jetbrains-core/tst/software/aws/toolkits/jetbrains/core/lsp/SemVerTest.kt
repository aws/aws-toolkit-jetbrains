// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SemVerTest {

    @Test
    fun `parse standard version`() {
        val ver = SemVerParser.parse("1.4.2")
        assertThat(ver).isNotNull
        assertThat(ver?.major).isEqualTo(1)
        assertThat(ver?.minor).isEqualTo(4)
        assertThat(ver?.patch).isEqualTo(2)
        assertThat(ver?.prerelease).isEmpty()
    }

    @Test
    fun `parse strips v prefix`() {
        val ver = SemVerParser.parse("v2.0.1")
        assertThat(ver).isNotNull
        assertThat(ver?.major).isEqualTo(2)
    }

    @Test
    fun `parse prerelease identifiers and build metadata`() {
        val ver = SemVerParser.parse("1.0.0-beta.1+build.7")
        assertThat(ver).isNotNull
        assertThat(ver?.prerelease).containsExactly("beta", "1")
    }

    @Test
    fun `compare prerelease numeric identifiers numerically`() {
        val betaTwo = checkNotNull(SemVerParser.parse("1.0.0-beta.2"))
        val betaTen = checkNotNull(SemVerParser.parse("1.0.0-beta.10"))

        assertThat(betaTen).isGreaterThan(betaTwo)
    }

    @Test
    fun `parse returns null for invalid version`() {
        assertThat(SemVerParser.parse("not-a-version")).isNull()
        assertThat(SemVerParser.parse("1.2")).isNull()
        assertThat(SemVerParser.parse("")).isNull()
    }

    @Test
    fun `compares major versions`() {
        val v1 = SemVerValue(1, 0, 0)
        val v2 = SemVerValue(2, 0, 0)
        assertThat(v1).isLessThan(v2)
    }

    @Test
    fun `compares minor versions`() {
        val v1 = SemVerValue(1, 2, 0)
        val v2 = SemVerValue(1, 3, 0)
        assertThat(v1).isLessThan(v2)
    }

    @Test
    fun `compares patch versions`() {
        val v1 = SemVerValue(1, 0, 1)
        val v2 = SemVerValue(1, 0, 2)
        assertThat(v1).isLessThan(v2)
    }

    @Test
    fun `release is greater than prerelease`() {
        val release = SemVerValue(1, 0, 0)
        val prerelease = SemVerValue(1, 0, 0, listOf("beta"))
        assertThat(release).isGreaterThan(prerelease)
    }

    @Test
    fun `numeric sort not lexicographic`() {
        val v9 = SemVerValue(9, 0, 0)
        val v10 = SemVerValue(10, 0, 0)
        assertThat(v10).isGreaterThan(v9)
    }

    @Test
    fun `range less-than excludes boundary`() {
        val range = SemVerParser.parseRange("<2.0.0")
        assertThat(range.satisfiedBy(SemVerValue(1, 9, 9))).isTrue()
        assertThat(range.satisfiedBy(SemVerValue(2, 0, 0))).isFalse()
        assertThat(range.satisfiedBy(SemVerValue(2, 0, 1))).isFalse()
    }

    @Test
    fun `range greater-equal includes boundary`() {
        val range = SemVerParser.parseRange(">=1.0.0")
        assertThat(range.satisfiedBy(SemVerValue(1, 0, 0))).isTrue()
        assertThat(range.satisfiedBy(SemVerValue(0, 9, 9))).isFalse()
    }

    @Test
    fun `range equality requires the same core version`() {
        val singleEquals = SemVerParser.parseRange("=1.2.3")
        val doubleEquals = SemVerParser.parseRange("==1.2.3")

        assertThat(singleEquals.satisfiedBy(SemVerValue(1, 2, 3))).isTrue()
        assertThat(doubleEquals.satisfiedBy(SemVerValue(1, 2, 3))).isTrue()
        assertThat(doubleEquals.satisfiedBy(SemVerValue(1, 2, 3, listOf("beta")))).isFalse()
        assertThat(doubleEquals.satisfiedBy(SemVerValue(1, 2, 4))).isFalse()
    }

    @Test
    fun `compound range works`() {
        val range = SemVerParser.parseRange(">=1.0.0 <2.0.0")
        assertThat(range.satisfiedBy(SemVerValue(1, 5, 0))).isTrue()
        assertThat(range.satisfiedBy(SemVerValue(0, 9, 0))).isFalse()
        assertThat(range.satisfiedBy(SemVerValue(2, 0, 0))).isFalse()
    }
}
