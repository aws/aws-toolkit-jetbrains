// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.intellij

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SdkVersionTest(private val sdkVersion: String, private val expected: Int?) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data(): Collection<Array<Any?>> = listOf(
            // marketing form, published once a release GAs
            arrayOf("2026.2", 262),
            arrayOf("2026.2.0.1", 262),
            arrayOf("2025.3", 253),
            arrayOf("2024.3", 243),

            // marketing form with a pre-release qualifier (Rider publishes these while still EAP)
            arrayOf("2026.3-EAP3-SNAPSHOT", 263),
            arrayOf("2026.2-RC1-SNAPSHOT", 262),

            // branch form, the only form published for IDEA/Gateway during the EAP window
            arrayOf("263.5153.40-EAP-SNAPSHOT", 263),
            arrayOf("263.5153-EAP-CANDIDATE-SNAPSHOT", 263),
            arrayOf("262.8665.258", 262),
            arrayOf("263-EAP-SNAPSHOT", 263),

            arrayOf("LATEST-EAP-SNAPSHOT", null)
        )
    }

    @Test
    fun `normalizes SDK coordinate to a branch number`() {
        assertThat(sdkBranchNumber(sdkVersion)).isEqualTo(expected)
    }

    @Test
    fun `treats 253 and newer as unified IntelliJ IDEA`() {
        assertThat(isUnifiedIdea(sdkVersion)).isEqualTo((expected ?: 0) >= 253)
    }
}
