// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleListCellRenderer
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.CfnOperationStatusService.Companion.isFailure
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.CfnOperationStatusService.Companion.isTerminal
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.CfnStatusBarWidgetFactory.Companion.OPERATIONS_TITLE
import java.time.Duration
import java.time.Instant
import javax.swing.Icon

internal class CfnStatusBarWidgetFactory : StatusBarEditorBasedWidgetFactory() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = DISPLAY_NAME
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = CfnStatusBarWidget(project)
    override fun canBeEnabledOn(statusBar: StatusBar) = true

    companion object {
        const val ID = "aws.toolkit.cloudformation.operation.status"
        const val DISPLAY_NAME = "AWS CloudFormation"
        const val OPERATIONS_TITLE = "AWS CloudFormation Operations"
    }
}

private class CfnStatusBarWidget(
    private val project: Project,
) : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    override fun ID(): String = CfnStatusBarWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon? {
        val unreleased = CfnOperationStatusService.getInstance(project).getActiveOperations()
        if (unreleased.isEmpty()) return null
        val hasActive = unreleased.any { !it.phase.isTerminal() }
        val hasFailed = unreleased.any { it.phase.isFailure() }
        return when {
            hasActive -> AnimatedIcon.Default()
            hasFailed -> AllIcons.General.Error
            else -> AllIcons.General.InspectionsOK
        }
    }

    override fun getSelectedValue(): String? {
        val text = CfnOperationStatusService.getInstance(project).getStatusText()
        return text.ifEmpty { null }
    }

    override fun getTooltipText(): String = OPERATIONS_TITLE

    override fun getPopup(): com.intellij.openapi.ui.popup.JBPopup? {
        val service = CfnOperationStatusService.getInstance(project)
        val operations = service.getAllOperations()
        if (operations.isEmpty()) return null

        return JBPopupFactory.getInstance()
            .createPopupChooserBuilder(operations)
            .setTitle(OPERATIONS_TITLE)
            .setRenderer(
                SimpleListCellRenderer.create { label, op: OperationInfo, _ ->
                    label.icon = when {
                        !op.phase.isTerminal() -> AnimatedIcon.Default()
                        op.phase.isFailure() -> AllIcons.General.Error
                        else -> AllIcons.General.InspectionsOK
                    }
                    val elapsed = formatElapsed(op.startTime)
                    val changeSetInfo = op.changeSetName?.let { " • $it" } ?: ""
                    label.text = "${op.stackName} — ${op.type.name.lowercase().replaceFirstChar { it.uppercase() }}$changeSetInfo ($elapsed)"
                }
            )
            .setItemChosenCallback {}
            .createPopup()
    }

    override fun dispose() {}
}

private fun formatElapsed(startTime: Instant): String {
    val seconds = Duration.between(startTime, Instant.now()).seconds
    return if (seconds < 60) "${seconds}s ago" else "${seconds / 60}m ago"
}
