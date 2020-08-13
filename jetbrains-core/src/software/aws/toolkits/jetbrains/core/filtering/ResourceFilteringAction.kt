// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.util.ElementsChooser
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import icons.AwsIcons
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
        val chooser = ElementsChooser(mutableListOf("element2"), true)
        val actiongroup = object : ActionGroup(), DumbAware {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
                object : ActionGroup("TODO add", null, AllIcons.General.Add), DumbAware {
                    override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
                        object : DumbAwareAction("TODO cloudformation", null, AwsIcons.Resources.CLOUDFORMATION_STACK) {
                            override fun actionPerformed(e: AnActionEvent) {
                                FilterDialogWrapper(project, FilterDialogWrapper.FilterType.CloudFormation).show()
                            }
                        },
                        object : DumbAwareAction("TODO tag", null, AwsIcons.Logos.AWS) {
                            override fun actionPerformed(e: AnActionEvent) {
                                FilterDialogWrapper(project, FilterDialogWrapper.FilterType.Tag).show()
                            }
                        }
                    )
                },
                object : DumbAwareAction("TODO edit", null, AllIcons.Actions.Edit) {
                    override fun actionPerformed(e: AnActionEvent) {
                        FilterDialogWrapper(project).showAndGet()
                    }

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = chooser.selectedElements.size == 1
                    }
                }, Separator(),
                object : DumbAwareAction("TODO set none", null, AllIcons.Actions.Unselectall) {
                    override fun actionPerformed(e: AnActionEvent) {
                        chooser.setAllElementsMarked(false)
                    }
                },
                object : DumbAwareAction("TODO select all", null, AllIcons.Actions.Selectall) {
                    override fun actionPerformed(e: AnActionEvent) {
                        chooser.setAllElementsMarked(true)
                    }
                },
                object : DumbAwareAction("TODO invert", null, AllIcons.Actions.SwapPanels) {
                    override fun actionPerformed(e: AnActionEvent) {
                        chooser.invertSelection()
                    }
                }
            )
        }

        val buttons = ActionManager.getInstance().createActionToolbar("TODOSuperCool", actiongroup, true)
        val panel = JPanel()
        panel.layout = BoxLayout(panel, 1)
        panel.add(chooser)
        panel.add(buttons.component)
        val popup = JBPopupFactory
            .getInstance()
            .createComponentPopupBuilder(panel, null as JComponent?).setFocusable(false).setRequestFocus(false).setResizable(true)
            .setMinSize(Dimension(200, 200))
            .setDimensionServiceKey(project, "DatabaseViewActions_Filter", false)
            .createPopup()
        val eventSource = e.inputEvent.source as JComponent
        popup.setMinimumSize(eventSource.size)
        popup.showUnderneathOf(eventSource)
        // AllIcons.General.Filter
        //FilterDialogWrapper(project).show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val icon = this.templatePresentation.icon
        val active = ResourceFilterManager.getInstance(project).filtersEnabled()
        // Give the icon the small green dot if active
        e.presentation.icon = if (active) ExecutionUtil.getLiveIndicator(icon) else icon
    }

    fun showDialog(project: Project) {
        FilterDialogWrapper(project).showAndGet()
        // TODO then update the contents
    }
}
