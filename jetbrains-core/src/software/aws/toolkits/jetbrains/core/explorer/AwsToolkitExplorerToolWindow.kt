// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.jetbrains.core.credentials.ChangeSettingsMode
import software.aws.toolkits.jetbrains.core.credentials.ProjectLevelSettingSelector
import software.aws.toolkits.jetbrains.core.credentials.SettingsSelectorComboBoxAction
import software.aws.toolkits.jetbrains.core.experiments.isEnabled
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.DevToolsToolWindow
import software.aws.toolkits.jetbrains.services.codewhisperer.experiment.CodeWhispererExperiment
import software.aws.toolkits.resources.message
import java.awt.Component

class AwsToolkitExplorerToolWindow(private val project: Project) : SimpleToolWindowPanel(true, true) {
    private val tabPane = JBTabbedPane()

    private val tabComponents = mapOf<String, () -> Component>(
        EXPLORER_TAB_ID to { ExplorerToolWindow.getInstance(project) },
        DEVTOOLS_TAB_ID to { DevToolsToolWindow.getInstance(project) }
    )

    init {
        runInEdt {
            val content = BorderLayoutPanel()
            setContent(content)
            val group = DefaultActionGroup(
                SettingsSelectorComboBoxAction(ProjectLevelSettingSelector(project, ChangeSettingsMode.CREDENTIALS)),
                SettingsSelectorComboBoxAction(ProjectLevelSettingSelector(project, ChangeSettingsMode.REGIONS))
            )

            toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true).apply {
                layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
                setTargetComponent(this@AwsToolkitExplorerToolWindow)
            }.component

            // main content
            val toolWindow = toolWindow(project)
            tabComponents.forEach { name, contentProvider ->
                tabPane.addTab(name, contentProvider())
            }
            content.addToCenter(tabPane)

            val toolkitToolWindowListener = ToolkitToolWindowListener(project)
            val onTabChange = {
                toolkitToolWindowListener.tabChanged(tabPane.getTitleAt(tabPane.selectedIndex))
            }
            tabPane.model.addChangeListener {
                onTabChange()
            }
            onTabChange()

            // Hide dev tool on initialization if CodeWhisperer is not enabled in experimental feature.
            // We can do this since CodeWhisperer is the only node in Dev Tool pane for now.
            if (!CodeWhispererExperiment.isEnabled()) {
                setDevToolsTabVisible(false)
            }

            toolWindow.contentManager.apply {
                addContent(
                    factory.createContent(this@AwsToolkitExplorerToolWindow, null, false).also {
                        it.isCloseable = true
                        it.isPinnable = true
                    }
                )
            }
        }
    }

    fun setDevToolsTabVisible(visible: Boolean) {
        val index = tabPane.indexOfTab(DEVTOOLS_TAB_ID)
        if (index == -1) {
            if (!visible) {
                // if we don't need to be visible we're done
                return
            }

            // else we need content
            tabComponents.onEachIndexed { i, (name, contentProvider) ->
                if (name == DEVTOOLS_TAB_ID) {
                    tabPane.insertTab(name, null, contentProvider(), null, i)
                    tabPane.selectedIndex = i
                    return
                }
            }
        }

        // we have content
        val content = tabPane.getComponentAt(index)
        if (!visible) {
            // remove if we want it gone
            content?.let {
                // only hide the compoenet instead of disposing all of them
                tabPane.removeTabAt(index)
            }
            return
        }

        // otherwise select the tab
        content?.let {
            tabPane.selectedIndex = index
        }
    }

    fun getTabLabelComponent(tabName: String): Component? {
        val index = tabPane.indexOfTab(tabName)
        if (index == -1) {
            return null
        }

        return tabPane.getTabComponentAt(index)
    }

    companion object {
        private val EXPLORER_TAB_ID = message("explorer.toolwindow.title")
        val DEVTOOLS_TAB_ID = message("aws.developer.tools.tab.title")

        fun getInstance(project: Project) = project.service<AwsToolkitExplorerToolWindow>()

        fun toolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(AwsToolkitExplorerFactory.TOOLWINDOW_ID)
            ?: error("Can't find AwsToolkitExplorerToolWindow")
    }
}
