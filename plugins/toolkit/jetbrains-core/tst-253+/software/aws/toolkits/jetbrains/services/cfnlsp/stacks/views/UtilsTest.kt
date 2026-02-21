// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.JBColor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UtilsTest {

    @Nested
    inner class StackStatusUtilsTest {

        @Test
        fun `getStatusColors returns green for COMPLETE statuses`() {
            val testCases = listOf(
                "CREATE_COMPLETE",
                "UPDATE_COMPLETE",
                "DELETE_COMPLETE"
            )

            testCases.forEach { status ->
                val (bgColor, fgColor) = StackStatusUtils.getStatusColors(status)
                assertThat(bgColor).isEqualTo(JBColor.GREEN)
                assertThat(fgColor).isEqualTo(JBColor.BLACK)
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
                assertThat(bgColor).isEqualTo(JBColor.RED)
                assertThat(fgColor).isEqualTo(JBColor.BLACK)
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
                assertThat(bgColor).isEqualTo(JBColor.YELLOW)
                assertThat(fgColor).isEqualTo(JBColor.BLACK)
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

    @Nested
    inner class StackDateFormatterTest {

        @Test
        fun `formatDate formats valid ISO date string`() {
            val isoDate = "2024-01-15T10:30:45Z"
            val result = StackDateFormatter.formatDate(isoDate)

            // Should format to d/M/yyyy, h:mm:ss a pattern (timezone-dependent)
            assertThat(result).isNotNull()
            assertThat(result!!).contains("15/1/2024")
            assertThat(result).satisfiesAnyOf(
                { assertThat(it).contains("AM") },
                { assertThat(it).contains("PM") }
            )
            assertThat(result).contains(":30:45")
        }

        @Test
        fun `formatDate formats date with milliseconds`() {
            val isoDate = "2024-12-25T23:59:59.123Z"
            val result = StackDateFormatter.formatDate(isoDate)

            assertThat(result).isNotNull()
            assertThat(result!!).contains("25/12/2024")
            assertThat(result).satisfiesAnyOf(
                { assertThat(it).contains("AM") },
                { assertThat(it).contains("PM") }
            )
            assertThat(result).contains(":59:59")
        }

        @Test
        fun `formatDate returns null for invalid date`() {
            val invalidDate = "not-a-date"
            val result = StackDateFormatter.formatDate(invalidDate)

            assertThat(result).isNull()
        }

        @Test
        fun `formatDate returns null for empty string`() {
            val result = StackDateFormatter.formatDate("")
            assertThat(result).isNull()
        }

        @Test
        fun `formatDate returns null for malformed ISO date`() {
            val malformedDate = "2024-13-45T25:70:80Z"
            val result = StackDateFormatter.formatDate(malformedDate)

            assertThat(result).isNull()
        }
    }
}
