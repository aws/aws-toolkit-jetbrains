// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SemVerTest {

    @Test
    fun `parse standard version`() {
        val v = SemVer.parse("1.4.0")!!
        assertThat(v.major).isEqualTo(1)
        assertThat(v.minor).isEqualTo(4)
        assertThat(v.patch).isEqualTo(0)
        assertThat(v.prerelease).isEmpty()
    }

    @Test
    fun `parse version with v prefix`() {
        val v = SemVer.parse("v1.4.0")!!
        assertThat(v.major).isEqualTo(1)
        assertThat(v.minor).isEqualTo(4)
    }

    @Test
    fun `parse version with prerelease`() {
        val v = SemVer.parse("1.4.0-beta")!!
        assertThat(v.prerelease).containsExactly("beta")
    }

    @Test
    fun `parse returns null for invalid input`() {
        assertThat(SemVer.parse("not-a-version")).isNull()
        assertThat(SemVer.parse("1.2")).isNull()
        assertThat(SemVer.parse("")).isNull()
        assertThat(SemVer.parse("abc.def.ghi")).isNull()
    }

    @Test
    fun `comparison - major version difference`() {
        assertThat(SemVer.parse("2.0.0")!!).isGreaterThan(SemVer.parse("1.9.9")!!)
    }

    @Test
    fun `comparison - minor version difference`() {
        assertThat(SemVer.parse("1.5.0")!!).isGreaterThan(SemVer.parse("1.4.9")!!)
    }

    @Test
    fun `comparison - patch version difference`() {
        assertThat(SemVer.parse("1.4.1")!!).isGreaterThan(SemVer.parse("1.4.0")!!)
    }

    @Test
    fun `comparison - release beats prerelease`() {
        assertThat(SemVer.parse("1.4.0")!!).isGreaterThan(SemVer.parse("1.4.0-beta")!!)
    }

    @Test
    fun `comparison - equal versions`() {
        assertThat(SemVer.parse("1.4.0")!!).isEqualByComparingTo(SemVer.parse("1.4.0")!!)
    }

    @Test
    fun `comparison - v prefix ignored`() {
        assertThat(SemVer.parse("v1.4.0")!!).isEqualByComparingTo(SemVer.parse("1.4.0")!!)
    }

    @Test
    fun `comparison - 10 is greater than 9 (not lexicographic)`() {
        assertThat(SemVer.parse("10.0.0")!!).isGreaterThan(SemVer.parse("9.0.0")!!)
        assertThat(SemVer.parse("1.10.0")!!).isGreaterThan(SemVer.parse("1.9.0")!!)
    }

    @Test
    fun `sorting produces correct order`() {
        val versions = listOf("1.0.0", "1.4.0", "1.2.0", "1.3.1", "1.1.0")
            .map { SemVer.parse(it)!! }
            .sortedDescending()
            .map { "${it.major}.${it.minor}.${it.patch}" }

        assertThat(versions).containsExactly("1.4.0", "1.3.1", "1.2.0", "1.1.0", "1.0.0")
    }

    @Test
    fun `sorting with prereleases`() {
        val versions = listOf("1.4.0", "1.4.0-beta", "1.3.1", "1.3.1-beta")
            .map { SemVer.parse(it)!! }
            .sortedDescending()
            .map {
                "${it.major}.${it.minor}.${it.patch}" +
                    if (it.prerelease.isNotEmpty()) "-${it.prerelease.joinToString("-")}" else ""
            }

        assertThat(versions).containsExactly("1.4.0", "1.4.0-beta", "1.3.1", "1.3.1-beta")
    }
}

class SemVerRangeTest {

    @Test
    fun `less than range`() {
        val range = SemVerRange.parse("<2.0.0")
        assertThat(range.satisfiedBy(SemVer.parse("1.4.0")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("1.99.99")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("2.0.0")!!)).isFalse()
        assertThat(range.satisfiedBy(SemVer.parse("2.0.1")!!)).isFalse()
        assertThat(range.satisfiedBy(SemVer.parse("3.0.0")!!)).isFalse()
    }

    @Test
    fun `less than range includes prereleases`() {
        val range = SemVerRange.parse("<2.0.0")
        assertThat(range.satisfiedBy(SemVer.parse("1.4.0-beta")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("2.0.0-beta")!!)).isFalse()
    }

    @Test
    fun `greater than or equal range`() {
        val range = SemVerRange.parse(">=1.2.0")
        assertThat(range.satisfiedBy(SemVer.parse("1.2.0")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("1.4.0")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("1.1.0")!!)).isFalse()
    }

    @Test
    fun `combined range`() {
        val range = SemVerRange.parse(">=1.0.0 <2.0.0")
        assertThat(range.satisfiedBy(SemVer.parse("1.0.0")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("1.99.0")!!)).isTrue()
        assertThat(range.satisfiedBy(SemVer.parse("0.9.0")!!)).isFalse()
        assertThat(range.satisfiedBy(SemVer.parse("2.0.0")!!)).isFalse()
    }
}
