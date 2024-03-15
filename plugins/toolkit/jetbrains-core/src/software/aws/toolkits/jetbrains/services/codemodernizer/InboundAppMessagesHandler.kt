// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformActionMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.IncomingCodeTransformMessage

interface InboundAppMessagesHandler {
    suspend fun processTransformQuickAction(message: IncomingCodeTransformMessage.Transform)

    suspend fun processCodeTransformCancelAction(message: IncomingCodeTransformMessage.CodeTransformCancel)

    suspend fun processCodeTransformStartAction(message: IncomingCodeTransformMessage.CodeTransformStart)

    suspend fun processCodeTransformStopAction(message: IncomingCodeTransformMessage.CodeTransformStop)

    suspend fun processCodeTransformOpenTransformHub(message: IncomingCodeTransformMessage.CodeTransformOpenTransformHub)

    suspend fun processCodeTransformOpenMvnBuild(message: IncomingCodeTransformMessage.CodeTransformOpenMvnBuild)

    suspend fun processCodeTransformNewAction(message: IncomingCodeTransformMessage.CodeTransformNew)

    suspend fun processCodeTransformCommand(message: CodeTransformActionMessage)

    suspend fun processTabCreated(message: IncomingCodeTransformMessage.TabCreated)

    suspend fun processTabRemoved(message: IncomingCodeTransformMessage.TabRemoved)

    suspend fun processAuthFollowUpClick(message: IncomingCodeTransformMessage.AuthFollowUpWasClicked)
}
