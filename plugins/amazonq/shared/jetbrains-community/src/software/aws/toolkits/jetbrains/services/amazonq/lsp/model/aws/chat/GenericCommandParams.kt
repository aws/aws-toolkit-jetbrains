// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat


//https://github.com/aws/language-server-runtimes/blob/main/chat-client-ui-types/src/uiContracts.ts#L27
enum class TriggerType(val value: String) {
    HOTKEYS("hotkeys"),
    CLICK("click"),
    CONTEXT_MENU("contextMenu")
}

data class GenericCommandParams(
    val selection: String,
    val triggerType: TriggerType,
    val genericCommand: String
)
