// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.progress.EmptyProgressIndicator

data class DiffMetrics(
    val insertedLines: Int,
    val insertedCharacters: Int,
)

fun lineEnding(content: String, curr: Int, end: Int): Int {
    require(curr <= end) { "curr must be within end of range" }
    require(end <= content.length) { "end must be within content" }

    return if (curr == end) {
        -1
    } else if (content[curr] == '\r') {
        if ((curr + 1 < end) && (content[curr + 1] == '\n')) {
            2
        } else {
            1
        }
    } else if (content[curr] == '\n') {
        1
    } else {
        -1
    }
}

fun getDiffMetrics(before: String, after: String): DiffMetrics {
    val comparisonManager = ComparisonManager.getInstance()
    val fragments = comparisonManager.compareLines(
        before,
        after,
        ComparisonPolicy.DEFAULT,
        EmptyProgressIndicator()
    )

    var accLineCount = 0
    var accCharCount = 0

    fragments.forEach { fragment: LineFragment ->
        var curr = fragment.startOffset2
        val end = fragment.endOffset2

        while (curr < end) {
            accLineCount += 1

            // Consume leading whitespace:
            while (curr < end && lineEnding(after, curr, end) == -1 && after[curr].isWhitespace()) curr++

            // Consume through EOL:
            val lineContentStart = curr
            while (curr < end && lineEnding(after, curr, end) == -1) curr++
            var lineContentEnd = curr
            curr += maxOf(lineEnding(after, curr, end), 0)

            // Walk back trailing whitespace and record character count before continuing to next line:
            while (lineContentEnd > lineContentStart && after[lineContentEnd - 1].isWhitespace()) lineContentEnd--
            accCharCount += lineContentEnd - lineContentStart
        }
    }

    return DiffMetrics(
        insertedLines = accLineCount,
        insertedCharacters = accCharCount,
    )
}
