// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

sealed interface LspApi {
    val command: String

    data object Initialize : LspApi {
        override val command: String = "initialize"
    }

    data object BuildIndex : LspApi {
        override val command: String = "buildIndex"
    }

    data object UpdateIndex : LspApi {
        override val command: String = "updateIndexV2"
    }

    data object QueryChat : LspApi {
        override val command: String = "query"
    }

    data object QueryInline : LspApi {
        override val command: String = "queryInlineProjectContext"
    }

    data object GetUsageMetrics : LspApi {
        override val command: String = "getUsage"
    }
}
