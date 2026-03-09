// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

enum class StackViewTab(val index: Int, val title: String) {
    OVERVIEW(0, "Overview"),
    EVENTS(1, "Events"),
    RESOURCES(2, "Resources"),
    OUTPUTS(3, "Outputs"),
    CHANGE_SET(4, "Change Set"),
}

internal class StackViewPanelTabber(
    project: Project,
    internal val stackName: String,
    private val stackArn: String,
) : Disposable {

    private val coordinator = StackViewCoordinator.getInstance(project)
    private val poller = StackStatusPoller(project, stackName, stackArn, coordinator)
    private val overviewPanel = StackOverviewPanel(project, coordinator, stackArn, stackName)
    private val resourcesPanel = StackResourcesPanel(project, coordinator, stackArn, stackName)
    private val outputsPanel = StackOutputsPanel(project, coordinator, stackArn, stackName)
    private val eventsPanel = StackEventsPanel(project, coordinator, stackArn, stackName)

    private val tabbedPane = JBTabbedPane().apply {
        addTab(StackViewTab.OVERVIEW.title, createOverviewPanel())
        addTab(StackViewTab.EVENTS.title, createEventsPanel())
        addTab(StackViewTab.RESOURCES.title, createResourcesPanel())
        addTab(StackViewTab.OUTPUTS.title, createOutputsPanel())
        selectedIndex = StackViewTab.OVERVIEW.index
    }

    private val changeSetTabIndex: Int
        get() = if (tabbedPane.tabCount > StackViewTab.CHANGE_SET.index) StackViewTab.CHANGE_SET.index else -1

    fun updateChangeSetTab(title: String, component: JComponent, tooltip: String? = null) {
        if (changeSetTabIndex >= 0) {
            tabbedPane.setComponentAt(StackViewTab.CHANGE_SET.index, component)
            tabbedPane.setTitleAt(StackViewTab.CHANGE_SET.index, title)
            tabbedPane.setToolTipTextAt(StackViewTab.CHANGE_SET.index, tooltip)
        } else {
            tabbedPane.insertTab(title, null, component, tooltip, StackViewTab.CHANGE_SET.index)
        }
        tabbedPane.selectedIndex = StackViewTab.CHANGE_SET.index
    }

    fun removeChangeSetTab() {
        if (changeSetTabIndex >= 0) tabbedPane.removeTabAt(StackViewTab.CHANGE_SET.index)
    }

    fun switchToTab(tab: StackViewTab) {
        tabbedPane.selectedIndex = tab.index
    }

    fun restartStatusPolling() {
        poller.start()
    }

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createOverviewPanel(): JComponent = overviewPanel.component

    private fun createResourcesPanel(): JComponent = resourcesPanel.component

    private fun createEventsPanel(): JComponent = eventsPanel.component

    private fun createOutputsPanel(): JComponent = outputsPanel.component

    fun start() {
        coordinator.setStack(stackArn, stackName)
        poller.setViewVisible(true)
    }

    fun getComponent(): JPanel = mainPanel

    override fun dispose() {
        poller.dispose()
        overviewPanel.dispose()
        resourcesPanel.dispose()
        outputsPanel.dispose()
        eventsPanel.dispose()
        coordinator.removeStack(stackArn)
    }
}
