// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class Model(
    val id: String,
    val name: String,
)

data class ListAvailableModelsParams(
    val tabId: String,
)

data class ListAvailableModelsResult(
    val tabId: String,
    val models: List<Model>,
    val selectedModelId: String? = null,
)
