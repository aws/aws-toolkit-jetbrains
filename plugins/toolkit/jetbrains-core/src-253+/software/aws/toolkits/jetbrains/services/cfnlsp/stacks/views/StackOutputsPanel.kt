// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackDetail
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackOutput
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ConsoleUrlGenerator
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.IconUtils
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.StackConsoleTab
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class StackOutputsPanel(
    project: Project,
    coordinator: StackViewCoordinator,
    stackArn: String,
    private val stackName: String,
) : Disposable, StackPanelListener {

    private val cfnClientService = CfnClientService.getInstance(project)
    private val disposables = mutableListOf<Disposable>()

    private var outputs: List<StackOutput> = emptyList()
    private var currentStackId: String? = null

    // UI Components using StackPanelLayoutBuilder
    internal val outputTable = StackPanelLayoutBuilder.createOutputsTable()
    internal val outputCountLabel = JBLabel("0 outputs").apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    internal val consoleLink = JBLabel(IconUtils.createBlueIcon(AllIcons.Ide.External_link_arrow)).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isVisible = false
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                currentStackId?.let { stackId ->
                    val consoleUrl = ConsoleUrlGenerator.generateTabUrl(stackId, StackConsoleTab.OUTPUTS)
                    BrowserUtil.browse(consoleUrl)
                }
            }
        })
    }

    val component: JComponent = StackPanelLayoutBuilder.createTablePanel(
        "Stack: $stackName",
        consoleLink,
        outputCountLabel,
        outputTable
    )

    init {
        disposables.add(coordinator.addListener(stackArn, this))
    }

    override fun onStackUpdated() {
        loadOutputs()
    }

    private fun loadOutputs() {
        cfnClientService.describeStack(DescribeStackParams(stackName))
            .thenApply { result -> result?.stack }
            .whenComplete { result, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        LOG.warn("Failed to load outputs for stack $stackName", error)
                        renderError("Failed to load outputs: ${error.message}")
                    } else {
                        result?.let { renderOutputs(it) } ?: renderEmpty()
                    }
                }
            }
    }

    fun renderOutputs(stack: StackDetail) {
        outputs = stack.outputs
        currentStackId = stack.stackId
        consoleLink.isVisible = stack.stackId.isNotEmpty()

        StackPanelLayoutBuilder.updateOutputsTable(outputTable, outputs)
        updateOutputCount(outputs.size)
    }

    private fun renderEmpty() {
        outputs = emptyList()
        StackPanelLayoutBuilder.updateOutputsTable(outputTable, outputs)
        updateOutputCount(0)
        consoleLink.isVisible = false
    }

    private fun renderError(message: String) {
        outputs = emptyList()
        StackPanelLayoutBuilder.updateOutputsTable(outputTable, emptyList(), message)
        updateOutputCount(0)
        consoleLink.isVisible = false
    }

    private fun updateOutputCount(count: Int) {
        outputCountLabel.text = "$count output${if (count != 1) "s" else ""}"
    }

    override fun dispose() {
        disposables.forEach { it.dispose() }
        disposables.clear()
    }

    companion object {
        private val LOG = getLogger<StackOutputsPanel>()
    }
}
