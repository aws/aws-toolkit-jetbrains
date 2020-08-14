// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.icons.AllIcons
import com.intellij.ide.util.ElementsChooser
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import icons.AwsIcons
import software.aws.toolkits.resources.message

class ResourceFilterActionGroup(
    private val project: Project,
    private val popup: JBPopup,
    private val chooser: ElementsChooser<String>
) : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
        object : ActionGroup(message("explorer.filter.add"), null, AllIcons.General.Add), DumbAware {
            init {
                isPopup = true
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
                object : DumbAwareAction(message("explorer.filter.cloudformation"), null, AwsIcons.Resources.CLOUDFORMATION_STACK) {
                    override fun actionPerformed(e: AnActionEvent) {
                        popup.cancel()
                        FilterDialogWrapper(project, FilterDialogWrapper.FilterType.CloudFormation).show()
                    }
                },
                object : DumbAwareAction(message("explorer.filter.tag"), null, AwsIcons.Logos.AWS) {
                    override fun actionPerformed(e: AnActionEvent) {
                        popup.cancel()
                        FilterDialogWrapper(project, FilterDialogWrapper.FilterType.Tag).show()
                    }
                }
            )
        },
        object : DumbAwareAction("explorer.filter.delete", null, AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) {
                chooser.selectedElements.forEach {
                    // remove from the manager and the chooser
                    ResourceFilterManager.getInstance(project).state.remove(it)
                    chooser.removeElement(it)
                }
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = chooser.selectedElements.isNotEmpty()
            }
        },
        object : DumbAwareAction(message("explorer.filter.edit"), null, AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                val name = chooser.selectedElements.firstOrNull() ?: return
                val filter = ResourceFilterManager.getInstance(project).state[name]
                if (filter == null) {
                    chooser.removeElement(name)
                    return
                }
                popup.cancel()
                val filterType = when (filter) {
                    is StackFilter -> FilterDialogWrapper.FilterType.CloudFormation
                    is TagFilter -> FilterDialogWrapper.FilterType.Tag
                }
                val dialogWrapper = FilterDialogWrapper(project, filterType)
                dialogWrapper.load(name, filter)
                dialogWrapper.show()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = chooser.selectedElements.size == 1
            }
        },
        Separator(),
        object : DumbAwareAction(message("explorer.filter.select_all"), null, AllIcons.Actions.Selectall) {
            override fun actionPerformed(e: AnActionEvent) {
                chooser.setAllElementsMarked(true)
            }
        },
        object : DumbAwareAction(message("explorer.filter.select_none"), null, AllIcons.Actions.Unselectall) {
            override fun actionPerformed(e: AnActionEvent) {
                chooser.setAllElementsMarked(false)
            }
        }
    )
}
