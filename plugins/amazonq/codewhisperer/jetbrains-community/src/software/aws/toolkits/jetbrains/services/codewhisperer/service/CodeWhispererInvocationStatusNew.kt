// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger

class CodeWhispererInvocationStatusNew: CodeWhispererInvocationStatus() {

    override fun startInvocation() {
        isInvokingService.set(true)
        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_INVOCATION_STATE_CHANGED).invocationStateChanged(true)
        LOG.debug { "Starting CodeWhisperer invocation" }
    }

    override fun checkExistingInvocationAndSet(): Boolean = false

    override fun setInvocationComplete() {}

    override fun hasEnoughDelayToShowCodeWhisperer(): Boolean = true

    companion object {
        fun getInstance(): CodeWhispererInvocationStatus = service()
    }
}
