// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.stack

import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import software.amazon.awssdk.services.cloudformation.model.Output
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class OutputsTableView : View, OutputsListener, Disposable {
    private val key = DynamicTableView.Field<Output>(message("cloudformation.stack.outputs.key")) { it.outputKey() }
    private val value = DynamicTableView.Field<Output>(message("cloudformation.stack.outputs.value")) { it.outputValue() }
    private val description = DynamicTableView.Field<Output>(message("cloudformation.stack.outputs.description")) { it.description() }
    private val export = DynamicTableView.Field<Output>(message("cloudformation.stack.outputs.export")) { it.exportName() }

    private val table = DynamicTableView(
        key,
        value,
        description,
        export
    ).apply {
        component.border = JBUI.Borders.empty()
    }

    init {
        table.addMouseListener(OutputActionPopup(this::selected))
    }

    override val component: JComponent = table.component

    private fun selected(): SelectedOutput? {
        val row = table.selectedRow() ?: return null
        return SelectedOutput(
            row[key] as? String,
            row[value] as? String,
            row[export] as? String
        )
    }

    override fun updatedOutputs(outputs: List<Output>) = table.updateItems(outputs.sortedBy { it.outputKey() }, clearExisting = true)

    override fun dispose() {}
}

interface OutputsListener {
    fun updatedOutputs(outputs: List<Output>)
}
