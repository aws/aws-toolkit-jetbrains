// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands

import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageTypeRegistry
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessageConnector
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.cwc.controller.TestCommandMessage

// Register Editor Actions in the Editor Context Menu
class ActionRegistrar {

    private val _messages by lazy { MutableSharedFlow<AmazonQMessage>(extraBufferCapacity = 10) }
    val flow = _messages.asSharedFlow()

    fun reportMessageClick(command: EditorContextCommand, project: Project) {
        // language=JSON
        AmazonQToolWindow.sendTestMessage(project)
        //AsyncChatUiListener.notifyPartialMessageUpdate(a)
       // _messages.tryEmit(ContextMenuActionMessage(command, project))
//        runBlocking {
//            MessageConnector().publish(messageToPublish)
//        }

    }

    fun reportMessageClick(command: EditorContextCommand, issue: MutableMap<String, String>, project: Project) {
        _messages.tryEmit(CodeScanIssueActionMessage(command, issue, project))
    }

    // provide singleton access
    companion object {
        val instance = ActionRegistrar()
    }
}
//fun getContext(project: Project) = AmazonQAppInitContext(
//    project,
//    MessageConnector(),
//    MessageConnector(),
//    MessageTypeRegistry(),
//    FqnWebviewAdapter(project)
//)
