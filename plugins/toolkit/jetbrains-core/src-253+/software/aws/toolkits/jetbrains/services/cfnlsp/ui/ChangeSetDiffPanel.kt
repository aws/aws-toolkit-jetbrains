// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceChange
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceChangeDetail
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackChange
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetDeletionWorkflow
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.DeploymentWorkflow
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views.StackViewWindowManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel

internal class ChangeSetDiffPanel(
    private val project: Project,
    private val stackName: String,
    private val changeSetName: String,
    private val changes: List<StackChange>,
    private val enableDeploy: Boolean,
) : SimpleToolWindowPanel(false, true) {

    private val resourceChanges = changes.mapNotNull { it.resourceChange }
    private val resourceTable = JBTable(ResourceTableModel(resourceChanges)).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setShowGrid(false)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) showResourceDiff()
            }
        })
        addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val row = rowAtPoint(Point(x, y))
                if (row < 0) return
                setRowSelectionInterval(row, row)
                val rc = resourceChanges.getOrNull(row) ?: return
                val menu = ActionManager.getInstance().createActionPopupMenu(
                    "ChangeSetDiffContext",
                    DefaultActionGroup(object : AnAction("View Diff", null, AllIcons.Actions.Diff) {
                        override fun actionPerformed(e: AnActionEvent) { showResourceDiff() }
                    })
                )
                menu.component.show(comp, x, y)
            }
        })
    }

    private val hasAnyDetails = resourceChanges.any { !it.details.isNullOrEmpty() }
    private val detailTable = JBTable().apply { setShowGrid(false) }
    private val detailPanel = JBPanel<JBPanel<*>>(BorderLayout())

    init {
        resourceTable.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) onResourceSelected()
        }

        val content = if (hasAnyDetails) {
            Splitter(true, 0.55f).apply {
                firstComponent = JBScrollPane(resourceTable)
                secondComponent = detailPanel
            }
        } else {
            JBScrollPane(resourceTable)
        }

        toolbar = createToolbar()
        setContent(content)
    }

    private fun onResourceSelected() {
        if (!hasAnyDetails) return
        val row = resourceTable.selectedRow
        if (row < 0 || row >= resourceChanges.size) return
        val rc = resourceChanges[row]
        val details = rc.details

        detailPanel.removeAll()
        if (!details.isNullOrEmpty()) {
            detailTable.model = DetailTableModel(details)
            detailPanel.add(JBScrollPane(detailTable), BorderLayout.CENTER)
        }
        detailPanel.revalidate()
        detailPanel.repaint()
    }

    private fun showResourceDiff() {
        val row = resourceTable.selectedRow
        if (row < 0 || row >= resourceChanges.size) return
        val rc = resourceChanges[row]
        val before = formatJson(rc.beforeContext ?: "")
        val after = formatJson(rc.afterContext ?: "")
        if (before.isEmpty() && after.isEmpty()) return

        val factory = DiffContentFactory.getInstance()
        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequest(
                "${rc.logicalResourceId ?: "Resource"} — $stackName",
                factory.create(before),
                factory.create(after),
                "Before",
                "After"
            )
        )
    }

    private fun formatJson(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(raw))
        } catch (_: Exception) {
            raw
        }
    }

    private fun createToolbar() = ActionManager.getInstance().createActionToolbar(
        "ChangeSetDiff",
        DefaultActionGroup().apply {
            if (enableDeploy) {
                add(object : AnAction("Deploy", "Execute this change set", AllIcons.Actions.Execute) {
                    override fun actionPerformed(e: AnActionEvent) {
                        DeploymentWorkflow(project).deploy(stackName, changeSetName)
                    }
                })
            }
            add(object : AnAction("Delete Change Set", "Delete this change set", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (Messages.showYesNoDialog(project, "Delete change set '$changeSetName'?", "Delete Change Set", null) == Messages.YES) {
                        ChangeSetDeletionWorkflow(project).delete(stackName, changeSetName)
                    }
                }
            })
        },
        true
    ).apply {
        targetComponent = this@ChangeSetDiffPanel
    }.component

    companion object {
        fun show(
            project: Project,
            stackName: String,
            changeSetName: String,
            changes: List<StackChange>,
            enableDeploy: Boolean,
        ) {
            val panel = ChangeSetDiffPanel(project, stackName, changeSetName, changes, enableDeploy)
            val windowManager = StackViewWindowManager.getInstance(project)
            var tabber = windowManager.getTabberByName(stackName)

            if (tabber == null) {
                // Open the stack view first
                try {
                    val stackResult = CfnClientService.getInstance(project)
                        .describeStack(DescribeStackParams(stackName)).get()
                    val stackId = stackResult?.stack?.stackId ?: return
                    windowManager.openStack(stackName, stackId)
                    tabber = windowManager.getTabberByName(stackName)
                } catch (_: Exception) {
                    return
                }
            }

            tabber?.updateChangeSetTab("Change set: $changeSetName", panel, tooltip = changeSetName)
        }
    }
}

private class ResourceTableModel(private val resources: List<ResourceChange>) : AbstractTableModel() {
    private val columns = arrayOf("Action", "Logical ID", "Physical ID", "Type", "Replacement")

    override fun getRowCount() = resources.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any {
        val rc = resources[row]
        return when (col) {
            0 -> rc.action ?: ""
            1 -> rc.logicalResourceId ?: ""
            2 -> rc.physicalResourceId ?: ""
            3 -> rc.resourceType ?: ""
            4 -> rc.replacement ?: ""
            else -> ""
        }
    }
}

private class DetailTableModel(private val details: List<ResourceChangeDetail>) : AbstractTableModel() {
    private val columns = arrayOf("Attribute Change Type", "Name", "Requires Recreation", "Before Value", "After Value", "Change Source", "Causing Entity")

    override fun getRowCount() = details.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any {
        val d = details[row]
        val t = d.target
        return when (col) {
            0 -> t?.attributeChangeType ?: ""
            1 -> t?.name ?: t?.attribute ?: ""
            2 -> t?.requiresRecreation ?: ""
            3 -> t?.beforeValue ?: ""
            4 -> t?.afterValue ?: ""
            5 -> d.changeSource ?: ""
            6 -> d.causingEntity ?: ""
            else -> ""
        }
    }
}
