// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.model

import com.intellij.openapi.vfs.VirtualFile

data class PreviousUTGIterationContext(
    val buildLogFile: VirtualFile,
    val testLogFile: VirtualFile,
    val selectedFile: VirtualFile?,
    val buildAndExecuteMessageId: String?,
)
