// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("Filename")

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

internal data class DocumentMetadata(
    val uri: String,
    val fileName: String,
    val ext: String,
    val type: String,
    val cfnType: String,
    val languageId: String,
    val version: Int,
    val lineCount: Int,
)
