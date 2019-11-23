// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.execution

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.StatusText
import software.aws.toolkits.jetbrains.ui.LocalPathProjectBaseCellEditor
import software.aws.toolkits.resources.message
import javax.swing.SwingUtilities
import javax.swing.table.TableCellEditor

class ArtifactMappingsTable(project: Project) : ListTableWithButtons<ArtifactMapping>() {

    init {
        tableView.apply {
            emptyText.text = message("cloud_debug.ecs.run_config.container.artifacts.empty.text")

            emptyText.appendSecondaryText(
                message("cloud_debug.ecs.run_config.container.artifacts.add"),
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.linkColor())
            ) {
                stopEditing()
                val listModel = tableView.listTableModel
                listModel.addRow(ArtifactMapping())
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

    private val pathCellEditor = LocalPathProjectBaseCellEditor(project)
        .normalizePath(true)
        .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileDescriptor())

    override fun isEmpty(element: ArtifactMapping): Boolean = element.localPath.isNullOrEmpty() ||
        element.remotePath.isNullOrEmpty()

    override fun cloneElement(variable: ArtifactMapping): ArtifactMapping = variable.copy()

    override fun canDeleteElement(selection: ArtifactMapping): Boolean = true

    override fun createElement(): ArtifactMapping = ArtifactMapping()

    fun getArtifactMappings(): List<ArtifactMapping> = elements.toList()

    override fun createListModel(): ListTableModel<*> = ListTableModel<ArtifactMapping>(
        StringColumnInfo(
            message("cloud_debug.ecs.run_config.container.artifacts.local"),
            { it.localPath },
            { mapping, value -> mapping.localPath = value },
            { pathCellEditor }
        ),
        StringColumnInfo(
            message("cloud_debug.ecs.run_config.container.artifacts.remote"),
            { it.remotePath },
            { mapping, value -> mapping.remotePath = value }
        )
    )

    private inner class StringColumnInfo(
        name: String,
        private val retrieveFunc: (ArtifactMapping) -> String?,
        private val setFunc: (ArtifactMapping, String?) -> Unit,
        private val editor: () -> TableCellEditor? = { null }
    ) : ListTableWithButtons.ElementsColumnInfoBase<ArtifactMapping>(name) {
        override fun valueOf(item: ArtifactMapping): String? = retrieveFunc.invoke(item)

        override fun setValue(item: ArtifactMapping, value: String?) {
            if (value == valueOf(item)) {
                return
            }
            setFunc.invoke(item, value)
            setModified()
        }

        override fun getDescription(item: ArtifactMapping): String? = null

        override fun isCellEditable(item: ArtifactMapping): Boolean = true

        override fun getEditor(item: ArtifactMapping): TableCellEditor? = editor()
    }
}
