// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.treeStructure.SimpleTreeStructure
import software.aws.toolkits.jetbrains.services.s3.objectActions.CopyPathAction
import software.aws.toolkits.jetbrains.services.s3.objectActions.DeleteObjectAction
import software.aws.toolkits.jetbrains.services.s3.objectActions.DownloadObjectAction
import software.aws.toolkits.jetbrains.services.s3.objectActions.NewFolderAction
import software.aws.toolkits.jetbrains.services.s3.objectActions.RenameObjectAction
import software.aws.toolkits.jetbrains.services.s3.objectActions.UploadObjectAction
import software.aws.toolkits.jetbrains.ui.tree.AsyncTreeModel
import software.aws.toolkits.jetbrains.ui.tree.StructureTreeModel
import software.aws.toolkits.resources.message
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class S3ViewerPanel(disposable: Disposable, private val project: Project, private val virtualBucket: S3VirtualBucket) {
    val component: JComponent
    val treeTable: S3TreeTable
    private val structureTreeModel: StructureTreeModel<SimpleTreeStructure>
    private val rootNode: S3TreeDirectoryNode

    init {
        val key = S3Column(S3ColumnType.NAME)
        val size = S3Column(S3ColumnType.SIZE)
        val modified = S3Column(S3ColumnType.LAST_MODIFIED)
        rootNode = S3TreeDirectoryNode(virtualBucket, null, "")
        structureTreeModel = StructureTreeModel(
            SimpleTreeStructure.Impl(rootNode), disposable
        )
        val model = S3TreeTableModel(AsyncTreeModel(structureTreeModel, true, disposable), arrayOf(key, size, modified), structureTreeModel)
        treeTable = S3TreeTable(model, virtualBucket, project).also {
            it.setRootVisible(false)
            it.cellSelectionEnabled = false
            it.rowSelectionAllowed = true
            it.rowSorter = S3RowSorter(it.model)
            // prevent accidentally moving the columns around. We don't account for the ability
            // to do this anywhere so better be safe than sorry. TODO audit logic to allow this
            it.tableHeader.reorderingAllowed = false
            it.columnModel.getColumn(1).maxWidth = 120
        }
        component = addToolbar(treeTable).createPanel()
        val treeRenderer = S3TreeCellRenderer(treeTable)
        treeTable.setTreeCellRenderer(treeRenderer)
        val tableRenderer = DefaultTableCellRenderer().also { it.horizontalAlignment = SwingConstants.LEFT }
        treeTable.setDefaultRenderer(Any::class.java, tableRenderer)
        addTreeActions(treeTable)
    }

    private fun addToolbar(table: S3TreeTable): ToolbarDecorator {
        val group = makeActionGroup(table)
        group.addAction(object : AnAction(message("explorer.refresh.title"), null, AllIcons.Actions.Refresh), DumbAware {
            override fun actionPerformed(e: AnActionEvent) {
                rootNode.removeAllChildren()
                structureTreeModel.invalidate()
            }
        })
        return ToolbarDecorator
            .createDecorator(table)
            .setActionGroup(group)
    }

    private fun addTreeActions(table: S3TreeTable) {
        PopupHandler.installPopupHandler(
            table,
            makeActionGroup(table),
            ActionPlaces.EDITOR_POPUP,
            ActionManager.getInstance()
        )
    }

    private fun makeActionGroup(table: S3TreeTable): DefaultActionGroup {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(DownloadObjectAction(project, table))
        actionGroup.add(UploadObjectAction(project, table))
        actionGroup.add(Separator())
        actionGroup.add(NewFolderAction(project, table))
        actionGroup.add(RenameObjectAction(project, table))
        actionGroup.add(CopyPathAction(project, table))
        actionGroup.add(Separator())
        actionGroup.add(DeleteObjectAction(project, table))
        return actionGroup
    }
}
