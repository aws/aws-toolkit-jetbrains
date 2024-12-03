// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeScanResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants

@Service
class CodeScanMessageListener {
    private val _messages by lazy { MutableSharedFlow<CodeScanActionMessage>(extraBufferCapacity = 10) }
    val flow = _messages.asSharedFlow()

    fun onScanResult(result: CodeScanResponse?, scope: CodeWhispererConstants.CodeAnalysisScope, project: Project) {
        _messages.tryEmit(CodeScanActionMessage(CodeScanCommand.ScanComplete, scanResult = result, scope = scope, project = project))
    }
}
