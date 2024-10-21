// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

sealed interface LspMessage {
    val endpoint: String

    data object Initialize : LspMessage {
        override val endpoint: String = "initialize"
    }

    data object Index : LspMessage {
        override val endpoint: String = "indexFiles"
    }

    data object UpdateIndex : LspMessage {
        override val endpoint: String = "updateIndex"
    }

    data object QueryChat : LspMessage {
        override val endpoint: String = "query"
    }

    data object GetUsageMetrics : LspMessage {
        override val endpoint: String = "getUsage"
    }
}

interface LspRequest

data class IndexRequest(
    val filePaths: List<String>,
    val projectRoot: String,
    val refresh: Boolean,
) : LspRequest

data class UpdateIndexRequest(
    val filePath: String,
) : LspRequest

data class QueryChatRequest(
    val query: String,
) : LspRequest

data class LspResponse(
    val responseCode: Int,
    val responseBody: String,
)
