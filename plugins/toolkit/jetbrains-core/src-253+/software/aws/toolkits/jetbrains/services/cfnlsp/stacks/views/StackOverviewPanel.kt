// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackDetail
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ConsoleUrlGenerator
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.IconUtils
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.WrappingTextArea
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

class StackOverviewPanel(
    project: Project,
    private val coordinator: StackViewCoordinator,
) : Disposable, StackPanelListener {

    private val cfnClientService = CfnClientService.getInstance(project)
    private val disposables = mutableListOf<Disposable>()

    internal val consoleLink = JBLabel(IconUtils.createBlueIcon(AllIcons.Ide.External_link_arrow)).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isVisible = false
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                currentStackId?.let { stackId ->
                    val consoleUrl = ConsoleUrlGenerator.generateUrl(stackId)
                    BrowserUtil.browse(consoleUrl)
                }
            }
        })
    }

    internal val stackNameValue = JBLabel("-")
    internal var currentStackId: String? = null
    internal val statusValue = JBLabel("Loading...")
    internal val stackIdValue = WrappingTextArea("-")
    internal val descriptionValue = WrappingTextArea("-")
    internal val createdValue = JBLabel("-")
    internal val lastUpdatedValue = JBLabel("-")
    internal val statusReasonValue = WrappingTextArea("-")

    val component: JComponent = createPanel()

    init {
        disposables.add(coordinator.addListener(this))
        setupStyling()
    }

    private fun setupStyling() {
        listOf(stackNameValue, statusValue, stackIdValue, descriptionValue, createdValue, lastUpdatedValue, statusReasonValue).forEach { label ->
            label.font = label.font.deriveFont(Font.PLAIN)
        }

        statusValue.border = JBUI.Borders.empty(4, 8)
        statusValue.horizontalAlignment = JBLabel.CENTER
    }

    override fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean) {
        if (stackName != null && !isChangeSetMode) {
            stackNameValue.text = stackName
            renderEmpty() // Show loading state
            loadStackDetails(stackName)
        } else {
            renderEmpty() // Show empty state
        }
    }

    override fun onStackStatusChanged(status: String?) {
        coordinator.getCurrentStackName()?.let { loadStackDetails(it) }
    }

    private fun loadStackDetails(stackName: String) {
        cfnClientService.describeStack(DescribeStackParams(stackName))
            .thenApply { result -> result?.stack }
            .whenComplete { result, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        renderError("Failed to load stack: ${error.message}")
                    } else {
                        result?.let { renderStack(it) } ?: renderEmpty()
                    }
                }
            }
    }

    private fun createPanel(): JPanel = StackPanelLayoutBuilder.createFormPanel().apply {
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        var row = 0
        row = StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Stack Name", createStackNamePanel())
        row = StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Status", statusValue, fillNone = true)
        row = StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Stack ID", stackIdValue)
        row = StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Description", descriptionValue)
        row = StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Created", createdValue)
        row = StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Last Updated", lastUpdatedValue)
        StackPanelLayoutBuilder.addLabeledField(this, gbc, row, "Status Reason", statusReasonValue, isLast = true)

        StackPanelLayoutBuilder.addFiller(this, gbc, row)
    }

    private fun createStackNamePanel(): JPanel = JBPanel<JBPanel<*>>().apply {
        layout = FlowLayout(FlowLayout.LEFT, 0, 0)
        add(stackNameValue)
        add(Box.createHorizontalStrut(8))
        add(consoleLink)
    }

    override fun dispose() {
        disposables.forEach { it.dispose() }
        disposables.clear()
    }

    fun renderStack(stack: StackDetail) {
        stackNameValue.text = stack.stackName
        updateStatusDisplay(stack.stackStatus)
        consoleLink.isVisible = stack.stackId.isNotEmpty()

        updateConditionalField(stackIdValue, stack.stackId.takeIf { it.isNotEmpty() })
        updateConditionalField(descriptionValue, stack.description?.takeIf { it.isNotEmpty() })
        updateConditionalField(createdValue, StackDateFormatter.formatDate(stack.creationTime))
        updateConditionalField(lastUpdatedValue, StackDateFormatter.formatDate(stack.lastUpdatedTime))
        updateConditionalField(statusReasonValue, stack.stackStatusReason?.takeIf { it.isNotEmpty() })

        currentStackId = stack.stackId
    }

    private fun renderEmpty() {
        stackNameValue.text = "Select a stack to view details"
        statusValue.text = "-"
        stackIdValue.text = "-"
        createdValue.text = "-"
        statusReasonValue.text = "-"
        resetStatusStyling()
        consoleLink.isVisible = false
    }

    private fun renderError(message: String) {
        stackNameValue.text = "Error"
        statusValue.text = "Error"
        stackIdValue.text = message
        createdValue.text = "-"
        statusReasonValue.text = "-"
        resetStatusStyling()
        consoleLink.isVisible = false
    }

    private fun updateStatusDisplay(status: String) {
        statusValue.text = status
        val (bgColor, fgColor) = StackStatusUtils.getStatusColors(status)

        if (bgColor != null) {
            statusValue.isOpaque = true
            statusValue.background = bgColor
            statusValue.foreground = fgColor
            statusValue.font = statusValue.font.deriveFont(12.0f)
        } else {
            resetStatusStyling()
        }
    }

    private fun resetStatusStyling() {
        statusValue.isOpaque = false
        statusValue.foreground = JBColor.foreground()
        statusValue.font = statusValue.font.deriveFont(Font.PLAIN)
    }

    private fun updateConditionalField(field: JComponent, value: String?) {
        if (value != null) {
            when (field) {
                is JBLabel -> field.text = value
                is JBTextArea -> field.text = value
            }
            setFieldVisibility(field, true)
        } else {
            setFieldVisibility(field, false)
        }
    }

    private fun setFieldVisibility(field: JComponent, visible: Boolean) {
        field.isVisible = visible
        val parent = field.parent
        if (parent != null) {
            val fieldIndex = parent.components.indexOf(field)
            if (fieldIndex > 0) {
                parent.components[fieldIndex - 1].isVisible = visible
            }
        }
    }
}
