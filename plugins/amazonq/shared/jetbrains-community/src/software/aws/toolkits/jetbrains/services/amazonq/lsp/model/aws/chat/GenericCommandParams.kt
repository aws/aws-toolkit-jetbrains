// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

// https://github.com/aws/language-server-runtimes/blob/main/chat-client-ui-types/src/uiContracts.ts#L27
enum class TriggerType(val value: String) {
    HOTKEYS("hotkeys"),
    CLICK("click"),
    CONTEXT_MENU("contextMenu"),
}

data class GenericCommandParams(
    val tabId: String? = null,
    val selection: String,
    val triggerType: TriggerType,
    val genericCommand: String,
)

// https://github.com/aws/language-server-runtimes/blob/b7c4718b9bd84e08e72b992da5d699549af9f115/chat-client-ui-types/src/uiContracts.ts#L67
data class SendToPromptParams(
    val selection: String,
    val triggerType: TriggerType,
    val prompt: ChatPrompt? = null,
    val autoSubmit: Boolean? = null
)
