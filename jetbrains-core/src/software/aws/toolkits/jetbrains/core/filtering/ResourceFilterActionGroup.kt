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

class ResourceFilterActionGroup(
    private val project: Project,
    private val popup: JBPopup,
    private val chooser: ElementsChooser<String>
) : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
        object : ActionGroup("TODO add", null, AllIcons.General.Add), DumbAware {
            init {
                isPopup = true
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
                object : DumbAwareAction("TODO cloudformation", null, AwsIcons.Resources.CLOUDFORMATION_STACK) {
                    override fun actionPerformed(e: AnActionEvent) {
                        popup.cancel()
                        FilterDialogWrapper(project, FilterDialogWrapper.FilterType.CloudFormation).showAndGet()
                    }
                },
                object : DumbAwareAction("TODO tag", null, AwsIcons.Logos.AWS) {
                    override fun actionPerformed(e: AnActionEvent) {
                        popup.cancel()
                        FilterDialogWrapper(project, FilterDialogWrapper.FilterType.Tag).show()
                    }
                }
            )
        },
        object : DumbAwareAction("TODO remove", null, AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) {
                val element = chooser.selectedElements.firstOrNull() ?: return
                // remove from the manager and the chooser
                ResourceFilterManager.getInstance(project).state.remove(element)
                chooser.removeElement(element)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = chooser.selectedElements.size == 1
            }
        },
        object : DumbAwareAction("TODO edit", null, AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO get type
                popup.cancel()
                FilterDialogWrapper(project, FilterDialogWrapper.FilterType.Tag).showAndGet()
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
        }
    )
}
