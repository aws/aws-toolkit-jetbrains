// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan

import software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands.CodeScanActionMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.IncomingCodeScanMessage

interface InboundAppMessagesHandler {
    suspend fun processScanQuickAction(message: IncomingCodeScanMessage.Scan)

    suspend fun processStartProjectScan(message: IncomingCodeScanMessage.StartProjectScan)

    suspend fun processStartFileScan(message: IncomingCodeScanMessage.StartFileScan)

    suspend fun processStopProjectScan(message: IncomingCodeScanMessage.StopProjectScan)

    suspend fun processStopFileScan(message: IncomingCodeScanMessage.StopFileScan)

    suspend fun processTabCreated(message: IncomingCodeScanMessage.TabCreated)

    suspend fun processClearQuickAction(message: IncomingCodeScanMessage.ClearChat)

    suspend fun processHelpQuickAction(message: IncomingCodeScanMessage.Help)

    suspend fun processTabRemoved(message: IncomingCodeScanMessage.TabRemoved)

    suspend fun processCodeScanCommand(message: CodeScanActionMessage)

    suspend fun processResponseBodyLinkClicked(message: IncomingCodeScanMessage.ResponseBodyLinkClicked)

    suspend fun processOpenIssuesPanel(message: IncomingCodeScanMessage.OpenIssuesPanel)
}
