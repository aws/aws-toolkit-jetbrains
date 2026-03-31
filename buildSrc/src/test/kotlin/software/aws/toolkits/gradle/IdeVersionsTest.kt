// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import software.aws.toolkits.gradle.intellij.isUnifiedIde

@RunWith(Parameterized::class)
class IdeVersionsTest(private val version: String, private val expected: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "isUnifiedIde({0}) -> {1}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("2025.1", false),
            arrayOf("2025.2", false),
            arrayOf("2025.3", true),
            arrayOf("2026.1", true),
            arrayOf("2026.2", true),
            arrayOf("2027.1", true),

            // Invalid inputs
            arrayOf("invalid", false),
            arrayOf("", false),
        )
    }

    @Test
    fun `isUnifiedIde returns correct result`() {
        assertThat(isUnifiedIde(version)).isEqualTo(expected)
    }
}
