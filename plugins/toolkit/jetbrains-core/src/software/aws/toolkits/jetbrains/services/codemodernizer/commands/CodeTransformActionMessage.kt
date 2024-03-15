// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.commands

import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult


data class CodeTransformActionMessage(
    val command: CodeTransformCommand,
    val result: CodeModernizerJobCompletedResult? = null
) : AmazonQMessage
