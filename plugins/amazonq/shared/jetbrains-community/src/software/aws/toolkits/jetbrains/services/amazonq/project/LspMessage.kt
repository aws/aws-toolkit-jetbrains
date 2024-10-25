// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

sealed interface LspMessage {
    val endpoint: String

    data object Initialize : LspMessage {
        override val endpoint: String = "initialize"
    }

    data object Index : LspMessage {
        override val endpoint: String = "buildIndex"
    }

    data object UpdateIndex : LspMessage {
        override val endpoint: String = "updateIndexV2"
    }

    data object QueryChat : LspMessage {
        override val endpoint: String = "query"
    }

    data object QueryInlineCompletion : LspMessage {
        override val endpoint: String = "queryInlineProjectContext"
    }

    data object GetUsageMetrics : LspMessage {
        override val endpoint: String = "getUsage"
    }
}

interface LspRequest

data class IndexRequest(
    val filePaths: List<String>,
    val projectRoot: String,
    val config: String,
    val language: String = "",
) : LspRequest

data class UpdateIndexRequest(
    val filePaths: List<String>,
    val mode: String,
) : LspRequest

data class QueryChatRequest(
    val query: String,
) : LspRequest

data class QueryInlineCompletionRequest(
    val query: String,
    val filePath: String,
) : LspRequest

data class LspResponse(
    val responseCode: Int,
    val responseBody: String,
)

enum class IndexUpdateMode(val command: String) {
    UPDATE("update"),
    REMOVE("remove"),
    ADD("add"),
}

enum class IndexOption(val command: String) {
    ALL("all"),
    DEFAULT("default"),
}

// TODO: unify with [software.aws.toolkits.jetbrains.services.codewhisperer.model.Chunk]
data class InlineBm25Chunk(
    val content: String,
    val filePath: String,
    val score: Double,
)
