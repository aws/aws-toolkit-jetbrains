// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow

import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener

class CodeWhispererCodeReferenceActionListener : CodeWhispererUserActionListener {
    override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, remainingRecomm: String) {
        val (project, editor) = states.requestContext
        val manager = CodeWhispererCodeReferenceManager.getInstance(project)
        manager.insertCodeReference(states, sessionContext.selectedIndex)
        manager.addListeners(editor)
    }
}
