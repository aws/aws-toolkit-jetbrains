// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ui.components.JBTextField
import software.aws.toolkits.jetbrains.services.sqs.MAX_LENGTH_OF_FIFO_ID
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel

class FifoPanel {
    lateinit var component: JPanel
    lateinit var deduplicationId: JBTextField
    lateinit var groupId: JBTextField
    lateinit var deduplicationContextHelp: JLabel
    lateinit var groupContextHelp: JLabel

    init {
        loadComponents()
        setFields()
    }

    private fun loadComponents() {
        deduplicationContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.message.deduplication_id.tooltip"))
            installOn(deduplicationContextHelp)
        }
        groupContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.message.group_id.tooltip"))
            installOn(groupContextHelp)
        }
    }

    private fun setFields() {
        deduplicationId.emptyText.text = message("sqs.required.empty.text")
        groupId.emptyText.text = message("sqs.required.empty.text")
    }

    fun validateFields(): String? =
        if (deduplicationId.text.length > MAX_LENGTH_OF_FIFO_ID || groupId.text.length > MAX_LENGTH_OF_FIFO_ID) {
            message("sqs.message.validation.long.id")
        } else if (deduplicationId.text.isBlank()) {
            message("sqs.message.validation.empty.deduplication_id")
        } else if (groupId.text.isBlank()) {
            message("sqs.message.validation.empty.group_id")
        } else {
            null
        }
}
