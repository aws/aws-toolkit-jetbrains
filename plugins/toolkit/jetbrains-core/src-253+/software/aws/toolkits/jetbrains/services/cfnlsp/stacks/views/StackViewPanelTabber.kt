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
    private val stackName: String,
    private val stackArn: String, // Use ARN as primary identifier
) : Disposable {

    private val coordinator = StackViewCoordinator.getInstance(project)
    private val poller = StackStatusPoller(project, stackName, stackArn, coordinator)
    private val overviewPanel = StackOverviewPanel(project, coordinator, stackArn, stackName)

    private val tabbedPane = JBTabbedPane().apply {
        addTab("Overview", createOverviewPanel())
        addTab("Resources", createResourcesPanel())
        addTab("Events", createEventsPanel())
        addTab("Outputs", createOutputsPanel())
        selectedIndex = 0
    }

    // Used for future change set functionality
    fun addTab(title: String, component: JComponent, index: Int? = null) {
        if (index != null) {
            tabbedPane.insertTab(title, null, component, null, index)
        } else {
            tabbedPane.addTab(title, component)
        }
    }

    // Used for future change set functionality
    fun removeTab(title: String) {
        for (i in 0 until tabbedPane.tabCount) {
            if (tabbedPane.getTitleAt(i) == title) {
                tabbedPane.removeTabAt(i)
                break
            }
        }
    }

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createOverviewPanel(): JComponent = overviewPanel.component

    private fun createResourcesPanel(): JPanel = JBPanel<JBPanel<*>>().apply {
        add(JBLabel("Stack Resources - Coming Soon"))
    }

    private fun createEventsPanel(): JPanel = JBPanel<JBPanel<*>>().apply {
        add(JBLabel("Stack Events - Coming Soon"))
    }

    private fun createOutputsPanel(): JPanel = JBPanel<JBPanel<*>>().apply {
        add(JBLabel("Stack Outputs - Coming Soon"))
    }

    fun start() {
        coordinator.setStack(stackArn, stackName)
        poller.setViewVisible(true)
    }

    fun getComponent(): JPanel = mainPanel

    override fun dispose() {
        poller.dispose()
        overviewPanel.dispose()
        coordinator.removeStack(stackArn)
    }
}
