// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

data class InlineBm25Chunk(
    val content: String,
    val filePath: String,
    val score: Double,
)
