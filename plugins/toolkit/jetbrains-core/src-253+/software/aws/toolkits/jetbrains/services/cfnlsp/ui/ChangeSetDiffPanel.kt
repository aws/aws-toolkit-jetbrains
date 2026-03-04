// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
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
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

private const val DRIFT_WARNING = "\u26A0\uFE0F"

internal class ChangeSetDiffPanel(
    private val project: Project,
    private val stackName: String,
    private val changeSetName: String,
    changes: List<StackChange>,
    private val enableDeploy: Boolean,
    private val status: String? = null,
    private val creationTime: String? = null,
    private val description: String? = null,
) : SimpleToolWindowPanel(false, true) {

    private val resourceChanges = changes.mapNotNull { it.resourceChange }
    private val hasDrift = resourceChanges.any { it.hasDrift() }
    private val resourceTable = JBTable(ResourceTableModel(resourceChanges, hasDrift)).apply {
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
                val menu = ActionManager.getInstance().createActionPopupMenu(
                    "ChangeSetDiffContext",
                    DefaultActionGroup(
                        object : AnAction("View Diff", null, AllIcons.Actions.Diff) {
                            override fun actionPerformed(e: AnActionEvent) {
                                showResourceDiff()
                            }
                        }
                    )
                )
                menu.component.show(comp, x, y)
            }
        })
    }

    private val hasAnyDetails = resourceChanges.any { !it.details.isNullOrEmpty() }
    private val detailTable = JBTable().apply { setShowGrid(false) }
    private val detailPanel = JBPanel<JBPanel<*>>(BorderLayout())

    init {
        if (hasDrift) applyDriftRenderer()

        resourceTable.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) onResourceSelected()
        }

        val headerPanel = buildHeaderPanel()

        val tableContent = if (hasAnyDetails) {
            Splitter(true, 0.55f).apply {
                firstComponent = JBScrollPane(resourceTable)
                secondComponent = detailPanel
            }
        } else {
            JBScrollPane(resourceTable)
        }

        val mainContent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(tableContent, BorderLayout.CENTER)
        }

        toolbar = createToolbar()
        setContent(mainContent)
    }

    private fun buildHeaderPanel(): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(6, 8)

        val parts = buildList {
            add(changeSetName)
            status?.let { add("Status: $it") }
            creationTime?.let { add("Created: $it") }
            description?.let { add(it) }
        }
        add(JBLabel(parts.joinToString("  |  ")).apply {
            foreground = JBColor.GRAY
        })
        add(javax.swing.JSeparator().apply { border = JBUI.Borders.emptyTop(4) })
    }

    private fun applyDriftRenderer() {
        val driftColIndex = resourceTable.columnCount - 1
        resourceTable.columnModel.getColumn(driftColIndex).cellRenderer = WarningCellRenderer()
    }

    private fun onResourceSelected() {
        if (!hasAnyDetails) return
        val row = resourceTable.selectedRow
        if (row < 0 || row >= resourceChanges.size) return
        val rc = resourceChanges[row]
        val details = rc.details

        detailPanel.removeAll()
        if (!details.isNullOrEmpty()) {
            val hasDetailDrift = details.any { it.target?.drift != null || it.target?.liveResourceDrift != null }
            detailTable.model = DetailTableModel(details, hasDetailDrift)
            if (hasDetailDrift) applyDetailDriftRenderer()
            val title = JBLabel("Property-level changes").apply {
                font = font.deriveFont(Font.BOLD)
                border = JBUI.Borders.empty(4, 6)
            }
            detailPanel.add(title, BorderLayout.NORTH)
            detailPanel.add(JBScrollPane(detailTable), BorderLayout.CENTER)
        }
        detailPanel.revalidate()
        detailPanel.repaint()
    }

    private fun applyDetailDriftRenderer() {
        val colCount = detailTable.columnCount
        val renderer = WarningCellRenderer()
        detailTable.columnModel.getColumn(colCount - 2).cellRenderer = renderer
        detailTable.columnModel.getColumn(colCount - 1).cellRenderer = renderer
    }

    private fun showResourceDiff() {
        val row = resourceTable.selectedRow
        if (row < 0 || row >= resourceChanges.size) return
        val rc = resourceChanges[row]
        val before = formatJson(rc.beforeContext ?: "")
        val after = formatJson(rc.afterContext ?: "")
        if (before.isEmpty() && after.isEmpty()) return

        val annotatedBefore = annotateDriftInJson(rc, before)
        val factory = DiffContentFactory.getInstance()
        val title = "${rc.logicalResourceId ?: "Resource"} — $stackName"
        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequest(
                title,
                factory.create(annotatedBefore, JsonFileType.INSTANCE),
                factory.create(after, JsonFileType.INSTANCE),
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

    private fun showAllResourcesDiff() {
        val beforeData = mutableMapOf<String, Any>()
        val afterData = mutableMapOf<String, Any>()

        for (rc in resourceChanges) {
            val id = rc.logicalResourceId ?: continue

            if (rc.action != "Add" || rc.resourceDriftStatus == "DELETED") {
                beforeData[id] = parseJsonOrEmpty(rc.beforeContext)
            }
            if (rc.action != "Remove") {
                afterData[id] = parseJsonOrEmpty(rc.afterContext)
            }

            if (rc.beforeContext == null && rc.afterContext == null) {
                rc.details?.forEach { detail ->
                    val target = detail.target ?: return@forEach
                    val name = target.name ?: return@forEach
                    if (rc.action != "Add") {
                        @Suppress("UNCHECKED_CAST")
                        (beforeData.getOrPut(id) { mutableMapOf<String, Any>() } as MutableMap<String, Any>)[name] =
                            target.beforeValue ?: "<UnknownBefore>"
                    }
                    if (rc.action != "Remove") {
                        @Suppress("UNCHECKED_CAST")
                        (afterData.getOrPut(id) { mutableMapOf<String, Any>() } as MutableMap<String, Any>)[name] =
                            target.afterValue ?: "<UnknownAfter>"
                    }
                }
            }
        }

        val gson = GsonBuilder().setPrettyPrinting().create()
        val beforeJson = gson.toJson(beforeData)
        val afterJson = gson.toJson(afterData)

        val annotatedBefore = annotateDriftInJsonAll(resourceChanges, beforeJson)
        val factory = DiffContentFactory.getInstance()
        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequest(
                "$stackName: Before \u2194 After",
                factory.create(annotatedBefore, JsonFileType.INSTANCE),
                factory.create(afterJson, JsonFileType.INSTANCE),
                "Before",
                "After"
            )
        )
    }

    private fun parseJsonOrEmpty(raw: String?): Any {
        if (raw.isNullOrBlank()) return emptyMap<String, Any>()
        return try {
            GsonBuilder().create().fromJson(raw, Map::class.java) ?: emptyMap<String, Any>()
        } catch (_: Exception) {
            emptyMap<String, Any>()
        }
    }

    private fun createToolbar() = ActionManager.getInstance().createActionToolbar(
        "ChangeSetDiff",
        DefaultActionGroup().apply {
            if (enableDeploy) {
                add(
                    object : AnAction(
                        "Execute Change Set",
                        "Execute this change set",
                        AllIcons.Actions.Execute,
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            DeploymentWorkflow(project).deploy(stackName, changeSetName)
                        }
                    }
                )
            }
            add(
                object : AnAction(
                    "View Diff",
                    "View side-by-side diff of all changes",
                    AllIcons.Actions.Diff,
                ) {
                    override fun actionPerformed(e: AnActionEvent) {
                        showAllResourcesDiff()
                    }
                }
            )
            add(
                object : AnAction(
                    "Delete Change Set",
                    "Delete this change set",
                    AllIcons.Actions.GC,
                ) {
                    override fun actionPerformed(e: AnActionEvent) {
                        val msg = "Delete change set '$changeSetName'?"
                        if (Messages.showYesNoDialog(project, msg, "Delete Change Set", null) == Messages.YES) {
                            ChangeSetDeletionWorkflow(project).delete(stackName, changeSetName)
                        }
                    }
                }
            )
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
            status: String? = null,
            creationTime: String? = null,
            description: String? = null,
        ) {
            val panel = ChangeSetDiffPanel(
                project, stackName, changeSetName, changes, enableDeploy,
                status, creationTime, description,
            )
            val windowManager = StackViewWindowManager.getInstance(project)
            var tabber = windowManager.getTabberByName(stackName)

            if (tabber == null) {
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

            tabber?.updateChangeSetTab("Change set", panel, tooltip = changeSetName)
        }
    }
}

internal fun annotateDriftInJson(rc: ResourceChange, beforeJson: String): String {
    val driftByPath = mutableMapOf<String, String>()

    if (rc.resourceDriftStatus == "DELETED") return "// $DRIFT_WARNING Resource deleted out-of-band\n$beforeJson"

    rc.details?.forEach { detail ->
        val target = detail.target ?: return@forEach
        val drift = target.drift ?: target.liveResourceDrift ?: return@forEach
        val path = target.path ?: return@forEach
        if (drift.actualValue == null) return@forEach
        driftByPath[path] = drift.actualValue
    }
    if (driftByPath.isEmpty()) return beforeJson

    val lines = beforeJson.lines().toMutableList()
    for ((path, actualValue) in driftByPath) {
        val lineIndex = findPropertyLine(lines, path)
        if (lineIndex >= 0) {
            lines[lineIndex] = "${lines[lineIndex]}  \u2190 $DRIFT_WARNING Drifted (Live AWS: $actualValue)"
        }
    }
    return lines.joinToString("\n")
}

private fun annotateDriftInJsonAll(resourceChanges: List<ResourceChange>, beforeJson: String): String {
    val lines = beforeJson.lines().toMutableList()
    for (rc in resourceChanges) {
        rc.details?.forEach { detail ->
            val target = detail.target ?: return@forEach
            val drift = target.drift ?: target.liveResourceDrift ?: return@forEach
            val path = target.path ?: return@forEach
            if (drift.actualValue == null) return@forEach

            val id = rc.logicalResourceId ?: return@forEach
            val resourceLine = lines.indexOfFirst { it.contains("\"$id\"") }
            if (resourceLine < 0) return@forEach

            if (rc.resourceDriftStatus == "DELETED") {
                lines[resourceLine] = "${lines[resourceLine]}  \u2190 $DRIFT_WARNING Resource deleted out-of-band"
                return@forEach
            }

            val lineIndex = findPropertyLine(lines, path, startFrom = resourceLine)
            if (lineIndex >= 0) {
                lines[lineIndex] = "${lines[lineIndex]}  \u2190 $DRIFT_WARNING Drifted (Live AWS: ${drift.actualValue})"
            }
        }
    }
    return lines.joinToString("\n")
}

private fun findPropertyLine(lines: List<String>, path: String, startFrom: Int = 0): Int {
    val parts = path.split("/").filter { it.isNotEmpty() }
    var currentLine = startFrom
    for (part in parts) {
        if (part.all { it.isDigit() }) continue
        val found = (currentLine + 1 until lines.size).firstOrNull { lines[it].contains("\"$part\"") }
        if (found == null) return -1
        currentLine = found
    }
    return currentLine
}

private fun ResourceChange.hasDrift(): Boolean =
    resourceDriftStatus != null ||
        details?.any { it.target?.drift != null || it.target?.liveResourceDrift != null } == true

internal fun ResourceChange.driftDisplay(): String {
    if (resourceDriftStatus == "DELETED") return "$DRIFT_WARNING Deleted"
    if (details?.any { it.target?.drift != null || it.target?.liveResourceDrift != null } == true) return "$DRIFT_WARNING Modified"
    if (resourceDriftStatus != null && resourceDriftStatus != "IN_SYNC") return "$DRIFT_WARNING $resourceDriftStatus"
    return "-"
}

private class ResourceTableModel(
    private val resources: List<ResourceChange>,
    private val showDrift: Boolean,
) : AbstractTableModel() {

    private val columns = if (showDrift) {
        arrayOf("Action", "Logical ID", "Physical ID", "Type", "Replacement", "Drift Status")
    } else {
        arrayOf("Action", "Logical ID", "Physical ID", "Type", "Replacement")
    }

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
            5 -> if (showDrift) rc.driftDisplay() else ""
            else -> ""
        }
    }
}

private class DetailTableModel(
    private val details: List<ResourceChangeDetail>,
    private val showDrift: Boolean,
) : AbstractTableModel() {

    private val columns = if (showDrift) {
        arrayOf(
            "Attribute Change Type", "Name", "Requires Recreation",
            "Before Value", "After Value", "Change Source", "Causing Entity",
            "Drift: Previous", "Drift: Actual",
        )
    } else {
        arrayOf(
            "Attribute Change Type", "Name", "Requires Recreation",
            "Before Value", "After Value", "Change Source", "Causing Entity",
        )
    }

    override fun getRowCount() = details.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any {
        val d = details[row]
        val t = d.target
        val drift = t?.drift ?: t?.liveResourceDrift
        return when (col) {
            0 -> t?.attributeChangeType ?: ""
            1 -> t?.name ?: t?.attribute ?: ""
            2 -> t?.requiresRecreation ?: ""
            3 -> t?.beforeValue ?: ""
            4 -> t?.afterValue ?: ""
            5 -> d.changeSource ?: ""
            6 -> d.causingEntity ?: ""
            7 -> if (showDrift) drift?.previousValue ?: "-" else ""
            8 -> if (showDrift) drift?.actualValue ?: "-" else ""
            else -> ""
        }
    }
}

private class WarningCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: javax.swing.JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val text = value?.toString() ?: ""
        if (text.isNotBlank() && text != "-") {
            foreground = if (isSelected) table.selectionForeground else JBColor.YELLOW.darker()
        }
        return comp
    }
}
