// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VersionsTest {
    @Test
    fun `no version ranges means any version is compatible`() {
        assertThat(isVersionValid(IntegerVersion(4), emptyList())).isEqualTo(Compatability.Valid)
    }

    @Test
    fun `version below all min versions returns VersionTooOld`() {
        assertThat(
            isVersionValid(
                IntegerVersion(4),
                listOf(
                    VersionRange(IntegerVersion(10), IntegerVersion(11)),
                    VersionRange(IntegerVersion(30), IntegerVersion(33)),
                    VersionRange(IntegerVersion(20), IntegerVersion(22))
                )
            )
        ).isInstanceOfSatisfying(Compatability.VersionTooOld::class.java) {
            assertThat(it.minVersion).isEqualTo(IntegerVersion(30))
        }
    }

    @Test
    fun `version above all max versions returns VersionTooNew`() {
        assertThat(
            isVersionValid(
                IntegerVersion(40),
                listOf(
                    VersionRange(IntegerVersion(10), IntegerVersion(11)),
                    VersionRange(IntegerVersion(30), IntegerVersion(33)),
                    VersionRange(IntegerVersion(20), IntegerVersion(22))
                )
            )
        ).isInstanceOfSatisfying(Compatability.VersionTooNew::class.java) {
            assertThat(it.maxVersion).isEqualTo(IntegerVersion(33))
        }
    }

    @Test
    fun `version in any range is valid`() {
        val ranges = listOf<VersionRange<Version>>(
            VersionRange(IntegerVersion(10), IntegerVersion(11)),
            VersionRange(IntegerVersion(30), IntegerVersion(33)),
            VersionRange(IntegerVersion(20), IntegerVersion(22))
        )

        assertThat(isVersionValid(IntegerVersion(11), ranges)).isEqualTo(Compatability.Valid)
        assertThat(isVersionValid(IntegerVersion(21), ranges)).isEqualTo(Compatability.Valid)
        assertThat(isVersionValid(IntegerVersion(31), ranges)).isEqualTo(Compatability.Valid)
    }

    @Test
    fun `minVersion is inclusive`() {
        val ranges = listOf<VersionRange<Version>>(
            VersionRange(IntegerVersion(10), IntegerVersion(11)),
        )

        assertThat(isVersionValid(IntegerVersion(10), ranges)).isEqualTo(Compatability.Valid)
    }

    @Test
    fun `maxVersion is exclusive`() {
        val ranges = listOf<VersionRange<Version>>(
            VersionRange(IntegerVersion(10), IntegerVersion(11)),
        )

        assertThat(isVersionValid(IntegerVersion(11), ranges)).isInstanceOf(Compatability.VersionTooNew::class.java)
    }

    data class IntegerVersion(val version: Int) : Version {
        override fun displayValue(): String = version.toString()

        override fun compareTo(other: Version): Int = version.compareTo((other as IntegerVersion).version)
    }
}
