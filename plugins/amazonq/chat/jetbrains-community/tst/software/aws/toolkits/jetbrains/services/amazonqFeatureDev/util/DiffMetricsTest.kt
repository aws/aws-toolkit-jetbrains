// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager

class DiffMetricsTest : LightPlatformTestCase() {
    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
    }

    fun `test empty input`() {
        val metrics = getDiffMetrics("", "")
        assertEquals(0, metrics.insertedLines)
        assertEquals(0, metrics.insertedCharacters)
    }

    fun `test insertions are counted`() {
        val before = """
            line1
            line2
        """.trimIndent()

        val after = """
            line1
            inserted
            line2
        """.trimIndent()

        val metrics = getDiffMetrics(before, after)
        assertEquals(1, metrics.insertedLines)
        assertEquals(8, metrics.insertedCharacters)
    }

    fun `test modifications are counted`() {
        val before = """
            line1
            line2
            line3
        """.trimIndent()

        val after = """
            line1
            modified
            line3
        """.trimIndent()

        val metrics = getDiffMetrics(before, after)
        assertEquals(1, metrics.insertedLines)
        assertEquals(8, metrics.insertedCharacters)
    }

    fun `test deletions are counted`() {
        val before = """
            line1
            line2
            line3
        """.trimIndent()

        val after = """
            line1
            line3
        """.trimIndent()

        val metrics = getDiffMetrics(before, after)
        assertEquals(0, metrics.insertedLines)
        assertEquals(0, metrics.insertedCharacters)
    }

    fun `test multiline and multiple hunks are counted`() {
        val before = """
            line1
            line2
            line3
        """.trimIndent()

        val after = """
            inserted1
            line1
            inserted2
            inserted3
            line3
            inserted4
        """.trimIndent()

        val metrics = getDiffMetrics(before, after)
        assertEquals(4, metrics.insertedLines)
        assertEquals(36, metrics.insertedCharacters)
    }

    fun `test empty lines are counted`() {
        val before = "line1"
        val after = "line1\n\nline2"
        val metrics = getDiffMetrics(before, after)
        assertEquals(2, metrics.insertedLines)
        assertEquals(5, metrics.insertedCharacters)
    }

    fun `test trailing newline is not counted`() {
        val before = "line1"
        val after = "line1\nline2\n"
        val metrics = getDiffMetrics(before, after)
        assertEquals(1, metrics.insertedLines)
        assertEquals(5, metrics.insertedCharacters)
    }

    fun `test newline sequences are counted`() {
        val before = "line1"
        val after = "line1\nline2\rline3\r\nline4"
        val metrics = getDiffMetrics(before, after)
        assertEquals(3, metrics.insertedLines)
        assertEquals(15, metrics.insertedCharacters)
    }

    fun `test leading and trailing whitespace are not counted as characters`() {
        val before = "line1\nline2"
        val after = "line1\n    after "

        val metrics = getDiffMetrics(before, after)
        assertEquals(1, metrics.insertedLines)
        assertEquals(5, metrics.insertedCharacters)
    }

    fun `test ignore whitespace change when performing diff`() {
        val before = "line1\nline2"
        val after = "line1\n    line2"

        val metrics = getDiffMetrics(before, after)
        assertEquals(0, metrics.insertedLines)
        assertEquals(0, metrics.insertedCharacters)
    }
}
