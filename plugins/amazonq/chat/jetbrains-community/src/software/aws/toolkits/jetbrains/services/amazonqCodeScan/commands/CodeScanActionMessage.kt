// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeScanResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants

data class CodeScanActionMessage(
    val command: CodeScanCommand,
    val project: Project,
    val scanResult: CodeScanResponse? = null,
    val scope: CodeWhispererConstants.CodeAnalysisScope,
) : AmazonQMessage
