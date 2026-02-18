// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel

internal class StackDetailView(
    project: Project,
    private val stackName: String,
    private val stackId: String,
) : Disposable {

    private val coordinator = StackViewCoordinator.getInstance(project)
    private val updater = StackDetailUpdater(project, stackName, coordinator)
    private val overviewPanel = StackOverviewPanel(project, coordinator)

    private val tabbedPane = JBTabbedPane().apply {
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
        add(createPanelsContainer(), BorderLayout.CENTER)
    }

    private fun createPanelsContainer(): JPanel {
        val overviewSectionPanel = createSectionPanel("OVERVIEW", overviewPanel.component)

        val splitter = JBSplitter(false, 0.3f).apply {
            firstComponent = overviewSectionPanel
            secondComponent = tabbedPane
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(splitter, BorderLayout.CENTER)
        }
    }

    private fun createSectionPanel(title: String, content: JComponent): JPanel =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.empty(8),
                JBUI.Borders.customLine(JBColor.border())
            )

            val titleLabel = JBLabel(title).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyBottom(8)
            }

            add(titleLabel, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }

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
        coordinator.setStack(stackName, stackId)
        updater.setViewVisible(true)
    }

    fun getComponent(): JPanel = mainPanel

    override fun dispose() {
        updater.dispose()
    }
}
