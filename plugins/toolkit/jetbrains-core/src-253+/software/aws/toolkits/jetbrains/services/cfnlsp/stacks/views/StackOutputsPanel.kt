// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

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
import javax.swing.JComponent

internal class StackOutputsPanel(
    project: Project,
    coordinator: StackViewCoordinator,
    stackArn: String,
    private val stackName: String,
) : Disposable, StackStatusListener {

    private val cfnClientService = CfnClientService.getInstance(project)
    private val disposables = mutableListOf<Disposable>()

    private var outputs: List<StackOutput> = emptyList()

    // UI Components using StackPanelLayoutBuilder
    internal val outputTable = StackPanelLayoutBuilder.createOutputsTable()
    internal val outputCountLabel = JBLabel("0 outputs").apply {
        foreground = UIUtil.getContextHelpForeground()
    }
    internal val consoleLink = IconUtils.createConsoleLinkIcon {
        ConsoleUrlGenerator.generateStackOutputsUrl(stackArn)
    }.apply {
        isVisible = false // Start hidden until successful load
    }

    val component: JComponent = StackPanelLayoutBuilder.createStackTablePanel(
        stackName,
        outputTable,
        outputCountLabel,
        consoleLink
    )

    init {
        disposables.add(coordinator.addStatusListener(stackArn, this))
    }

    override fun onStackStatusUpdated() {
        loadOutputs()
    }

    private fun loadOutputs() {
        cfnClientService.describeStack(DescribeStackParams(stackName))
            .thenApply { result -> result?.stack }
            .whenComplete { result, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        handleError("Failed to load outputs for stack $stackName: ${error.message}")
                    } else {
                        result?.let { renderOutputs(it) } ?: renderEmpty()
                    }
                }
            }
    }

    private fun renderOutputs(stack: StackDetail) {
        outputs = stack.outputs ?: emptyList()
        consoleLink.isVisible = stack.stackId.isNotEmpty()

        StackPanelLayoutBuilder.updateOutputsTable(outputTable, outputs)
        updateOutputCount(outputs.size)
    }

    private fun renderEmpty() {
        outputs = emptyList()
        consoleLink.isVisible = false
        StackPanelLayoutBuilder.updateOutputsTable(outputTable, outputs)
        updateOutputCount(0)
    }

    private fun handleError(message: String) {
        outputs = emptyList()
        consoleLink.isVisible = false
        StackPanelLayoutBuilder.updateOutputsTable(outputTable, emptyList(), message)
        updateOutputCount(0)
        LOG.warn(message)
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
