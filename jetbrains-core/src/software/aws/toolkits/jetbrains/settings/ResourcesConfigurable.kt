// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.CheckBoxList
import com.intellij.ui.FilterComponent
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.core.explorer.ExplorerToolWindow
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceSupportedTypes
import software.aws.toolkits.jetbrains.services.dynamic.explorer.OtherResourcesNode
import software.aws.toolkits.resources.message
import javax.swing.ListSelectionModel

class ResourcesConfigurable : BoundConfigurable(message("aws.settings.dynamic_resources_configurable.title")) {
    private val checklist = CheckBoxList<String>()
    private val allResources = mutableSetOf<String>()
    private val selected = mutableSetOf<String>()
    private val filter = object : FilterComponent("filter", 5) {
        override fun filter() {
            updateList()
        }
    }

    init {
        checklist.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        checklist.setCheckBoxListListener { idx, state ->
            val value = checklist.getItemAt(idx) ?: return@setCheckBoxListListener
            when (state) {
                true -> selected.add(value)
                false -> selected.remove(value)
            }
        }

        ListSpeedSearch(checklist) {
            it.text.substringAfter("::")
        }
    }

    override fun getPreferredFocusedComponent() = checklist

    override fun createPanel() = panel {
        selected.clear()
        selected.addAll(SelectedResourcesSettings.getInstance().selected)
        ApplicationManager.getApplication().executeOnPooledThread {
            allResources.addAll(DynamicResourceSupportedTypes.getInstance().getSupportedTypes())
            runInEdt(ModalityState.any()) {
                updateList()
            }
        }

        row {
            filter(growX)
        }

        row {
            // scrollpane
            scrollPane(checklist).constraints(growX, pushX)

            // select/clearall
            right {
                cell(isVerticalFlow = true) {
                    val sizeGroup = "buttons"
                    button(message("aws.settings.dynamic_resources_configurable.select_all")) {
                        setVisibleSelection(true)
                    }.sizeGroup(sizeGroup)
                    button(message("aws.settings.dynamic_resources_configurable.clear_all")) {
                        setVisibleSelection(false)
                    }.sizeGroup(sizeGroup)
                }
            }
        }
    }

    override fun isModified(): Boolean = selected != SelectedResourcesSettings.getInstance().selected

    override fun apply() {
        SelectedResourcesSettings.getInstance().selected(selected)
        refreshAwsExplorer()
    }

    private fun updateList() {
        checklist.clear()
        allResources.filter { it.contains(filter.filter, ignoreCase = true) }.sorted().forEach { checklist.addItem(it, it, it in selected) }
    }

    private fun setVisibleSelection(desiredState: Boolean) {
        (0 until checklist.model.size).forEach {
            val item = checklist.getItemAt(it) ?: return
            when (desiredState) {
                true -> selected.add(item)
                false -> selected.remove(item)
            }
        }
        updateList()
    }

    private fun refreshAwsExplorer() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                val toolWindow = ExplorerToolWindow.getInstance(project)
                toolWindow.findNode(OtherResourcesNode::class).then { node ->
                    node.let {
                        toolWindow.invalidateTree(it)
                    }
                }
            }
        }
    }
}
