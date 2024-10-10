// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.jetbrains.core.credentials.CredsComboBoxActionGroup
import software.aws.toolkits.jetbrains.core.explorer.cwqTab.CodewhispererQToolWindow
import software.aws.toolkits.jetbrains.core.explorer.cwqTab.isQInstalled
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.DevToolsToolWindow
import software.aws.toolkits.resources.message
import java.awt.Component

class AwsToolkitExplorerToolWindowState : BaseState() {
    var selectedTab by string()
}

@State(name = "explorerToolWindow", storages = [Storage("aws.xml")])
class AwsToolkitExplorerToolWindow(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), PersistentStateComponent<AwsToolkitExplorerToolWindowState> {
    private val tabPane = JBTabbedPane()

    // TODO: Should we persist dismissed state as == don't show again?
    val amazonQTabDismissAction = object : AnAction(
        message("codewhisperer.explorer.node.dismiss"),
        null,
        AllIcons.Windows.CloseActive
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            tabPane.remove(CodewhispererQToolWindow.getInstance(project))
        }
    }

    private val tabComponents = buildMap<String, () -> Component> {
        put(EXPLORER_TAB_ID, { ExplorerToolWindow.getInstance(project) })
        put(DEVTOOLS_TAB_ID, { DevToolsToolWindow.getInstance(project) })
        if (!isQInstalled()) {
            put(Q_TAB_ID, { CodewhispererQToolWindow.getInstance(project) })
        }
    }

    init {
        runInEdt {
            val content = BorderLayoutPanel()
            setContent(content)
            val group = CredsComboBoxActionGroup(project)

            toolbar = BorderLayoutPanel().apply {
                val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true).apply {
                    setTargetComponent(this@AwsToolkitExplorerToolWindow)
                }
                setToolbarLayout(actionToolbar)
                addToCenter(actionToolbar.component)

                val actionManager = ActionManager.getInstance()
                val rightActionGroup = DefaultActionGroup(
                    actionManager.getAction("aws.toolkit.toolwindow.credentials.rightGroup.more"),
                    actionManager.getAction("aws.toolkit.toolwindow.credentials.rightGroup.help")
                )

                addToRight(
                    ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, rightActionGroup, true).apply {
                        // revisit if these actions need the tool window as a data provider
                        setTargetComponent(component)
                    }.component
                )
            }

            // main content
            tabComponents.forEach { name, contentProvider ->
                tabPane.addTab(name, contentProvider())
            }
            content.addToCenter(tabPane)

            val toolkitToolWindowListener = ToolkitToolWindowListener(project)
            val onTabChange = {
                val index = tabPane.selectedIndex
                if (index != -1) {
                    toolkitToolWindowListener.tabChanged(tabPane.getTitleAt(index))
                }
            }
            tabPane.model.addChangeListener {
                onTabChange()
            }
            onTabChange()
        }
    }

    fun selectTab(tabName: String): Component? {
        val index = tabPane.indexOfTab(tabName)
        if (index == -1) {
            return null
        }

        val component = tabPane.getComponentAt(index)
        if (component != null) {
            tabPane.selectedComponent = tabPane.getComponentAt(index)

            return component
        }

        return null
    }

    fun getTabLabelComponent(tabName: String): Component? {
        val index = tabPane.indexOfTab(tabName)
        if (index == -1) {
            return null
        }

        return tabPane.getTabComponentAt(index)
    }

    override fun getState() = AwsToolkitExplorerToolWindowState().apply {
        val index = tabPane.selectedIndex
        if (index != -1) {
            selectedTab = tabPane.getTitleAt(tabPane.selectedIndex)
        }
    }

    override fun loadState(state: AwsToolkitExplorerToolWindowState) {
        selectTab(Q_TAB_ID)
    }

    companion object {
        val EXPLORER_TAB_ID = message("explorer.toolwindow.title")
        val DEVTOOLS_TAB_ID = message("aws.developer.tools.tab.title")
        val Q_TAB_ID = message("aws.codewhispererq.tab.title")

        fun getInstance(project: Project) = project.service<AwsToolkitExplorerToolWindow>()

        fun toolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(AwsToolkitExplorerFactory.TOOLWINDOW_ID)
            ?: error("Can't find AwsToolkitExplorerToolWindow")
    }
}
