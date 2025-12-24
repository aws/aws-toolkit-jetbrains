// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.services.telemetry

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.components.BorderLayoutPanel
import software.amazon.q.jetbrains.isDeveloperMode
import software.amazon.q.core.telemetry.MetricEvent
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class OpenTelemetryAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        TelemetryDialog().show()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isDeveloperMode()
    }

    private class TelemetryDialog : FrameWrapper(null), TelemetryListener {
        private val consoleView: ConsoleView by lazy {
            TextConsoleBuilderFactory.getInstance().createBuilder(DefaultProjectFactory.getInstance().defaultProject).apply {
                setViewer(true)
            }.console
        }
        private val telemetryEvents = mutableListOf<MetricEvent>()
        private var currentFilter = ""

        init {
            title = "Telemetry Viewer"
            component = createContent()
        }

        private fun createContent(): JComponent {
            val panel = BorderLayoutPanel()
            val consoleComponent = consoleView.component

            // Add search/filter bar at the top
            val filterPanel = JPanel(BorderLayout())
            val filterField = JBTextField().apply {
                emptyText.text = "Filter telemetry events..."
            }
            filterPanel.add(JBLabel("Filter: "), BorderLayout.WEST)
            filterPanel.add(filterField, BorderLayout.CENTER)

            val actionGroup = DefaultActionGroup(*consoleView.createConsoleActions())
            val toolbar = ActionManager.getInstance().createActionToolbar("AWS.TelemetryViewer", actionGroup, false)

            toolbar.setTargetComponent(consoleComponent)

            panel.addToTop(filterPanel)
            panel.addToLeft(toolbar.component)
            panel.addToCenter(consoleComponent)

            // Add a border to make things look nicer.
            consoleComponent.border = BorderFactory.createLineBorder(JBColor.GRAY)

            val telemetryService = TelemetryService.getInstance()
            telemetryService.addListener(this)
            Disposer.register(this) { telemetryService.removeListener(this) }
            Disposer.register(this, consoleView)

            // Implement filtering logic
            filterField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    // Filter console content based on filterField.text
                    applyFilter(filterField.text)
                }
            })

            return panel
        }

        private fun applyFilter(filterText: String) {
            currentFilter = filterText
            consoleView.clear()
            telemetryEvents.filter {
                it.toString().contains(filterText, ignoreCase = true)
            }.forEach { event ->
                consoleView.print(event.toString() + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }
        }

        override fun onTelemetryEvent(event: MetricEvent) {
            telemetryEvents.add(event)
            if (event.toString().contains(currentFilter, ignoreCase = true)) {
                consoleView.print(event.toString() + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }
        }
    }
}
