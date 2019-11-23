// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.execution

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.StatusText
import software.aws.toolkits.resources.message
import javax.swing.SwingUtilities

class PortMappingsTable : ListTableWithButtons<PortMapping>() {

    init {
        tableView.apply {
            emptyText.text = message("cloud_debug.ecs.run_config.container.ports.empty.text")

            emptyText.appendSecondaryText(
                message("cloud_debug.ecs.run_config.container.ports.add"),
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.linkColor())
            ) {
                stopEditing()
                val listModel = tableView.listTableModel
                listModel.addRow(PortMapping())
                val index = listModel.rowCount - 1
                tableView.setRowSelectionInterval(index, index)
                SwingUtilities.invokeLater {
                    TableUtil.scrollSelectionToVisible(tableView)
                    TableUtil.editCellAt(tableView, index, 0)
                }
            }

            val shortcutSet = CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD)
            val shortcut = ArrayUtil.getFirstElement(shortcutSet.shortcuts)
            if (shortcut != null) {
                emptyText.appendSecondaryText(" (${KeymapUtil.getShortcutText(shortcut)})", StatusText.DEFAULT_ATTRIBUTES, null)
            }
        }
    }

    override fun isEmpty(element: PortMapping): Boolean = element.localPort == null ||
        element.remotePort == null

    override fun cloneElement(variable: PortMapping): PortMapping = variable.copy()

    override fun canDeleteElement(selection: PortMapping): Boolean = true

    override fun createElement(): PortMapping = PortMapping()

    fun getPortMappings(): List<PortMapping> = elements.toList()

    override fun createListModel(): ListTableModel<*> = ListTableModel<PortMapping>(
        NumericColumnInfo(
            message("cloud_debug.ecs.run_config.container.ports.local"),
            { it.localPort },
            { mapping, value -> mapping.localPort = value }),
        NumericColumnInfo(
            message("cloud_debug.ecs.run_config.container.ports.remote"),
            { it.remotePort },
            { mapping, value -> mapping.remotePort = value })
    )

    private inner class NumericColumnInfo(
        name: String,
        private val retrieveFunc: (PortMapping) -> Int?,
        private val setFunc: (PortMapping, Int?) -> Unit
    ) : ListTableWithButtons.ElementsColumnInfoBase<PortMapping>(name) {
        override fun valueOf(item: PortMapping): String? = retrieveFunc.invoke(item).let {
            it?.toString() ?: ""
        }

        override fun setValue(item: PortMapping, value: String?) {
            if (value == valueOf(item)) {
                return
            }

            val trimmedInput = value?.trim()
            if (trimmedInput?.isNotEmpty() == true) {
                val valueInt = try {
                    Integer.parseInt(trimmedInput)
                } catch (_: Exception) {
                    return
                }

                if (valueInt > 0) {
                    setFunc.invoke(item, valueInt)
                    setModified()
                }
            }
        }

        override fun getDescription(item: PortMapping?): String? = null

        override fun isCellEditable(item: PortMapping?): Boolean = true
    }
}
