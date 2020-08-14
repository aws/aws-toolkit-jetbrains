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
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import software.aws.toolkits.jetbrains.core.explorer.redrawAwsTree
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
            emptyText.text = message("explorer.filter.empty")
            // populate with existing filters
            ResourceFilterManager.getInstance(project).state.forEach { (key, value) -> this@apply.addElement(key, value.enabled) }
            // Cannot use SAM without specifying the type because of overload resolution ambiguity
            this.addElementsMarkListener(ElementsChooser.ElementsMarkListener<String> { element, isMarked ->
                val manager = ResourceFilterManager.getInstance(project)
                val state = manager.state[element]
                // If we are somehow out of sync, like if the state was manually edited, remove the element
                if (state == null) {
                    this.removeElement(element)
                } else {
                    manager.state[element] = state.copy(isEnabled = isMarked)
                    project.redrawAwsTree()
                }
            })
        }
        // This popup is inspired by the IDE's Database filter popup
        val panel = JPanel()
        val popup = createPopup(panel)
        val buttons = ActionManager
            .getInstance()
            .createActionToolbar("ResourceFilteringAction", ResourceFilterActionGroup(project, popup, chooser), true)
        panel.layout = BoxLayout(panel, 1)
        panel.add(chooser)
        panel.add(buttons.component)

        popup.setMinimumSize(eventSource.size)
        popup.showUnderneathOf(eventSource)
    }

    private fun createPopup(panel: JComponent): JBPopup = JBPopupFactory
        .getInstance()
        .createComponentPopupBuilder(panel, null).setFocusable(false).setRequestFocus(false).setResizable(true)
        .setMinSize(Dimension(300, 300))
        .createPopup()

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val icon = this.templatePresentation.icon
        val active = ResourceFilterManager.getInstance(project).filtersEnabled()
        // Give the icon the small green dot if active
        e.presentation.icon = if (active) ExecutionUtil.getLiveIndicator(icon) else icon
    }
}
