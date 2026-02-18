// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StackDateFormatterTest {

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
    fun `formatDate returns null for null input`() {
        val result = StackDateFormatter.formatDate(null)
        assertThat(result).isNull()
    }

    @Test
    fun `formatDate returns original string for invalid date`() {
        val invalidDate = "not-a-date"
        val result = StackDateFormatter.formatDate(invalidDate)

        assertThat(result).isEqualTo("not-a-date")
    }

    @Test
    fun `formatDate returns original string for empty string`() {
        val result = StackDateFormatter.formatDate("")
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `formatDate returns original string for malformed ISO date`() {
        val malformedDate = "2024-13-45T25:70:80Z"
        val result = StackDateFormatter.formatDate(malformedDate)

        assertThat(result).isEqualTo("2024-13-45T25:70:80Z")
    }
}
