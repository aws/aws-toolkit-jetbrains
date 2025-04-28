// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.VisibleForTesting
import software.amazon.awssdk.services.codewhispererruntime.model.Completion
import software.amazon.awssdk.services.codewhispererruntime.model.Span
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationChunk
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCompletionType
import kotlin.math.max
import kotlin.math.min

@Service
class CodeWhispererRecommendationManager {
    fun buildRecommendationChunks(
        recommendation: String,
        matchingSymbols: List<Pair<Int, Int>>,
    ): List<RecommendationChunk> = matchingSymbols
        .dropLast(1)
        .mapIndexed { index, (offset, inlayOffset) ->
            val end = matchingSymbols[index + 1].first - 1
            RecommendationChunk(recommendation.substring(offset, end), offset, inlayOffset)
        }

    fun buildDetailContext(
        userInput: String,
        completions: InlineCompletionListWithReferences,
    ): MutableList<DetailContext> {
        return completions.items.map {
            DetailContext(
                it.itemId,
                it,
                isDiscarded = !it.insertText.startsWith(userInput) || it.insertText == userInput,
                getCompletionType(it)
            )
        }.toMutableList()
    }

    companion object {
        fun getInstance(): CodeWhispererRecommendationManager = service()

        /**
         * a function to trim extra prefixing new line character (only leave 1 new line character)
         * example:
         *  content = "\n\n\nfoo\n\nbar\nbaz"
         *  return = "\nfoo\n\nbar\nbaz"
         *
         * example:
         *  content = "\n\n\tfoobar\nbaz"
         *  return = "\n\tfoobar\nbaz"
         */
        fun trimExtraPrefixNewLine(content: String): String {
            if (content.isEmpty()) {
                return ""
            }

            val firstChar = content.first()
            if (firstChar != '\n') {
                return content
            }

            var index = 1
            while (index < content.length && content[index] == '\n') {
                index++
            }

            return firstChar + content.substring(index)
        }
    }
}
