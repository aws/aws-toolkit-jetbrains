// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.util.ElementsChooser
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import software.aws.toolkits.resources.message
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ResourceFilteringAction : DumbAwareAction(
    message("explorer.filter"),
    null,
    AllIcons.General.Filter
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(PlatformDataKeys.PROJECT)
        val eventSource = e.inputEvent.source as JComponent
        val chooser = ElementsChooser(mutableListOf<String>(), false).apply {
            emptyText.text = "TODO add a filter"
        }
        val buttons = ActionManager
            .getInstance()
            .createActionToolbar("ResourceFilteringAction", ResourceFilterActionGroup(project, chooser), true)
        val panel = JPanel()
        panel.layout = BoxLayout(panel, 1)
        panel.add(chooser)
        panel.add(buttons.component)
        showPopup(panel, eventSource)
    }

    private fun showPopup(panel: JComponent, source: JComponent) {
        val popup = JBPopupFactory
            .getInstance()
            .createComponentPopupBuilder(panel, null).setFocusable(false).setRequestFocus(false).setResizable(true)
            .setMinSize(Dimension(200, 200))
            //.setDimensionServiceKey(project, "DatabaseViewActions_Filter", false)
            .createPopup()
        popup.setMinimumSize(source.size)
        popup.showUnderneathOf(source)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val icon = this.templatePresentation.icon
        val active = ResourceFilterManager.getInstance(project).filtersEnabled()
        // Give the icon the small green dot if active
        e.presentation.icon = if (active) ExecutionUtil.getLiveIndicator(icon) else icon
    }
}
