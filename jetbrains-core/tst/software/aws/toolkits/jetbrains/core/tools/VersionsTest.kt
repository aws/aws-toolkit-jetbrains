// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.isInstanceOf
import software.aws.toolkits.jetbrains.utils.isInstanceOfSatisfying

class VersionsTest {
    @Test
    fun `no version ranges means any version is compatible`() {
        assertThat(IntegerVersion(4).isValid(emptyList())).isInstanceOf<Validity.Valid>()
    }

    @Test
    fun `version below all min versions returns VersionTooOld`() {
        assertThat(
            IntegerVersion(4).isValid(
                listOf(
                    VersionRange(IntegerVersion(10), IntegerVersion(11)),
                    VersionRange(IntegerVersion(30), IntegerVersion(33)),
                    VersionRange(IntegerVersion(20), IntegerVersion(22))
                )
            )
        ).isInstanceOfSatisfying<Validity.VersionTooOld> {
            assertThat(it.minVersion).isEqualTo(IntegerVersion(30))
        }
    }

    @Test
    fun `version above all max versions returns VersionTooNew`() {
        assertThat(
            IntegerVersion(40).isValid(
                listOf(
                    VersionRange(IntegerVersion(10), IntegerVersion(11)),
                    VersionRange(IntegerVersion(30), IntegerVersion(33)),
                    VersionRange(IntegerVersion(20), IntegerVersion(22))
                )
            )
        ).isInstanceOfSatisfying<Validity.VersionTooNew> {
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

        assertThat(IntegerVersion(11).isValid(ranges)).isInstanceOf<Validity.Valid>()
        assertThat(IntegerVersion(21).isValid(ranges)).isInstanceOf<Validity.Valid>()
        assertThat(IntegerVersion(31).isValid(ranges)).isInstanceOf<Validity.Valid>()
    }

    @Test
    fun `minVersion is inclusive`() {
        val ranges = listOf<VersionRange<Version>>(
            VersionRange(IntegerVersion(10), IntegerVersion(11)),
        )

        assertThat(IntegerVersion(10).isValid(ranges)).isInstanceOf<Validity.Valid>()
    }

    @Test
    fun `maxVersion is exclusive`() {
        val ranges = listOf<VersionRange<Version>>(
            VersionRange(IntegerVersion(10), IntegerVersion(11)),
        )

        assertThat(IntegerVersion(11).isValid(ranges)).isInstanceOf(Validity.VersionTooNew::class.java)
    }

    data class IntegerVersion(val version: Int) : Version {
        override fun displayValue(): String = version.toString()
        override fun compareTo(other: Version): Int = version.compareTo((other as IntegerVersion).version)
    }
}
