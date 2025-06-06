// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.importadder

import com.intellij.openapi.editor.RangeMarker
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings

object CodeWhispererImportAdderListener : CodeWhispererUserActionListener {
    internal val LOG = getLogger<CodeWhispererImportAdderListener>()
    override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, rangeMarker: RangeMarker) {
        if (!CodeWhispererSettings.getInstance().isImportAdderEnabled()) {
            LOG.debug { "Import adder not enabled in user settings" }
            return
        }
        val language = states.requestContext.fileContextInfo.programmingLanguage
        val importAdder = CodeWhispererImportAdder.get(language)
        if (importAdder == null) {
            LOG.debug { "No import adder found for $language" }
            return
        }
        importAdder.insertImportStatements(states, sessionContext)
    }

    override fun afterAccept(states: InvocationContextNew, previews: List<PreviewContext>, sessionContext: SessionContextNew, rangeMarker: RangeMarker) {
        if (!CodeWhispererSettings.getInstance().isImportAdderEnabled()) {
            LOG.debug { "Import adder not enabled in user settings" }
            return
        }
        val language = states.requestContext.fileContextInfo.programmingLanguage
        val importAdder = CodeWhispererImportAdder.get(language)
        if (importAdder == null) {
            LOG.debug { "No import adder found for $language" }
            return
        }
        importAdder.insertImportStatements(states, previews, sessionContext)
    }
}
