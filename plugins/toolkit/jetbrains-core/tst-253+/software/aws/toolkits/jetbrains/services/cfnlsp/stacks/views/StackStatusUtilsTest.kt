// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.JBColor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StackStatusUtilsTest {

    @Test
    fun `getStatusColors returns green for COMPLETE statuses`() {
        val testCases = listOf(
            "CREATE_COMPLETE",
            "UPDATE_COMPLETE",
            "DELETE_COMPLETE"
        )

        testCases.forEach { status ->
            val (bgColor, fgColor) = StackStatusUtils.getStatusColors(status)
            assertThat(bgColor).isEqualTo(JBColor(0x28A745, 0x28A745))
            assertThat(fgColor).isEqualTo(JBColor.WHITE)
        }
    }

    @Test
    fun `getStatusColors returns red for FAILED and ROLLBACK statuses`() {
        val testCases = listOf(
            "CREATE_FAILED",
            "UPDATE_FAILED",
            "ROLLBACK_COMPLETE",
            "UPDATE_ROLLBACK_COMPLETE"
        )

        testCases.forEach { status ->
            val (bgColor, fgColor) = StackStatusUtils.getStatusColors(status)
            assertThat(bgColor).isEqualTo(JBColor(0xDC3545, 0xDC3545))
            assertThat(fgColor).isEqualTo(JBColor.WHITE)
        }
    }

    @Test
    fun `getStatusColors returns yellow for PROGRESS statuses`() {
        val testCases = listOf(
            "CREATE_IN_PROGRESS",
            "UPDATE_IN_PROGRESS",
            "DELETE_IN_PROGRESS"
        )

        testCases.forEach { status ->
            val (bgColor, fgColor) = StackStatusUtils.getStatusColors(status)
            assertThat(bgColor).isEqualTo(JBColor(0xFFC107, 0xFFC107))
            assertThat(fgColor).isEqualTo(JBColor(0x212529, 0x212529))
        }
    }

    @Test
    fun `getStatusColors returns null for unknown statuses`() {
        val testCases = listOf("UNKNOWN_STATUS", "", "RANDOM_TEXT")

        testCases.forEach { status ->
            val (bgColor, fgColor) = StackStatusUtils.getStatusColors(status)
            assertThat(bgColor).isNull()
            assertThat(fgColor).isNull()
        }
    }

    @Test
    fun `isInTransientState returns true for IN_PROGRESS statuses`() {
        val testCases = listOf(
            "CREATE_IN_PROGRESS",
            "UPDATE_IN_PROGRESS",
            "DELETE_IN_PROGRESS",
            "UPDATE_CLEANUP_IN_PROGRESS"
        )

        testCases.forEach { status ->
            assertThat(StackStatusUtils.isInTransientState(status)).isTrue()
        }
    }

    @Test
    fun `isInTransientState returns false for terminal statuses`() {
        val testCases = listOf(
            "CREATE_COMPLETE",
            "UPDATE_COMPLETE",
            "CREATE_FAILED",
            "ROLLBACK_COMPLETE"
        )

        testCases.forEach { status ->
            assertThat(StackStatusUtils.isInTransientState(status)).isFalse()
        }
    }
}
