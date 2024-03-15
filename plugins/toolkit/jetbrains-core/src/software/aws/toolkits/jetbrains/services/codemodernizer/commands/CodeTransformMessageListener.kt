// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.commands

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult

class CodeTransformMessageListener {

    private val _messages by lazy { MutableSharedFlow<CodeTransformActionMessage>(extraBufferCapacity = 10) }
    val flow = _messages.asSharedFlow()

    fun onStart() {
        _messages.tryEmit(CodeTransformActionMessage(CodeTransformCommand.Start))
    }

    fun onStop() {
        _messages.tryEmit(CodeTransformActionMessage(CodeTransformCommand.Stop))
    }

    fun onCancel() {
        _messages.tryEmit(CodeTransformActionMessage(CodeTransformCommand.Cancel))
    }

    fun onTransformResult(result: CodeModernizerJobCompletedResult) {
        _messages.tryEmit(CodeTransformActionMessage(CodeTransformCommand.TransformComplete, result))
    }

    fun onTransformResuming() {
        _messages.tryEmit(CodeTransformActionMessage(CodeTransformCommand.TransformResuming))
    }

    // provide singleton access
    companion object {
        val instance = CodeTransformMessageListener()
    }
}
