// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.intellij

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReleaseChannelTest(private val sdkVersion: String, private val expected: ReleaseChannel) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data(): Collection<Array<Any>> = listOf(
            // GA / stable — clean marketing strings
            arrayOf("2026.2", ReleaseChannel.STABLE),
            arrayOf("2026.2.0.1", ReleaseChannel.STABLE),
            arrayOf("262.8665.258", ReleaseChannel.STABLE),

            // EAP — snapshot builds
            arrayOf("2026.2-SNAPSHOT", ReleaseChannel.EAP),
            arrayOf("2026.3-EAP-SNAPSHOT", ReleaseChannel.EAP),
            arrayOf("263-EAP-SNAPSHOT", ReleaseChannel.EAP),

            // RC — checked before EAP since RC snapshots also carry "SNAPSHOT"
            arrayOf("2026.2-RC1-SNAPSHOT", ReleaseChannel.RC),
            arrayOf("2026.2-RC2", ReleaseChannel.RC),

            // Beta
            arrayOf("2026.2-BETA", ReleaseChannel.BETA)
        )
    }

    @Test
    fun `classifies SDK coordinate channel`() {
        assertThat(classifyChannel(sdkVersion)).isEqualTo(expected)
    }
}
