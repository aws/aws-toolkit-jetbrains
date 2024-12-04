// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands.codescan.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands.CodeScanMessageListener
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.scanResultsKey
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.scanScopeKey

class CodeScanCompleteAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val result = e.getData(scanResultsKey)
        val scope = e.getData(scanScopeKey) ?: return
        service<CodeScanMessageListener>().onScanResult(result, scope, project)
    }
}
