// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws

data class InlineCompletionStates(
    val seen: Boolean,
    val accepted: Boolean,
    val discarded: Boolean,
)
