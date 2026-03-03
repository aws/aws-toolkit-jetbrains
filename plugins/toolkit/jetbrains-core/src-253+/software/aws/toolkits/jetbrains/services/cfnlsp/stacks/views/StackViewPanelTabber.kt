// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

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
        addTab("Overview", overviewPanel.component)
        addTab("Resources", resourcesPanel.component)
        addTab("Events", JBPanel<JBPanel<*>>().apply { add(JBLabel("Stack Events - Coming Soon")) })
        addTab("Outputs", outputsPanel.component)
        selectedIndex = 0
    }

    private val changeSetTabIndex: Int
        get() = if (tabbedPane.tabCount > CHANGE_SET_TAB_INDEX) CHANGE_SET_TAB_INDEX else -1

    fun updateChangeSetTab(title: String, component: JComponent, tooltip: String? = null) {
        if (changeSetTabIndex >= 0) {
            tabbedPane.setComponentAt(CHANGE_SET_TAB_INDEX, component)
            tabbedPane.setTitleAt(CHANGE_SET_TAB_INDEX, title)
            tabbedPane.setToolTipTextAt(CHANGE_SET_TAB_INDEX, tooltip)
        } else {
            tabbedPane.insertTab(title, null, component, tooltip, CHANGE_SET_TAB_INDEX)
        }
        tabbedPane.selectedIndex = CHANGE_SET_TAB_INDEX
    }

    fun removeChangeSetTab() {
        if (changeSetTabIndex >= 0) tabbedPane.removeTabAt(CHANGE_SET_TAB_INDEX)
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

    companion object {
        private const val CHANGE_SET_TAB_INDEX = 4
    }
}
