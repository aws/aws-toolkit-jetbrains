// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.CheckBoxList
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class ResourceTypeSelectionDialog(
    project: Project,
    private val availableTypes: List<String>,
    selectedTypes: Set<String> = emptySet(),
) : DialogWrapper(project) {

    var selectedResourceTypes: List<String> = emptyList()
        private set

    private val typesList = CheckBoxList<String>()
    private val searchField = SearchTextField(false)
    private val currentSelections = selectedTypes.toMutableSet() // Track selections separately

    init {
        title = message("cloudformation.explorer.resources.dialog.title")
        init()
        setupList()
        setupSearch()
    }

    private fun setupList() {
        // Add listener to track checkbox changes
        typesList.setCheckBoxListListener { index, value ->
            val item = typesList.getItemAt(index)
            if (item != null) {
                if (value) {
                    currentSelections.add(item)
                } else {
                    currentSelections.remove(item)
                }
            }
        }

        // Initial population - this will show pre-selected items as checked
        filterList()
    }

    private fun setupSearch() {
        searchField.textEditor.emptyText.text = "Search resource types..."
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterList()
            override fun removeUpdate(e: DocumentEvent?) = filterList()
            override fun changedUpdate(e: DocumentEvent?) = filterList()
        })
    }

    private fun filterList() {
        val searchText = searchField.text
        typesList.clear()

        val filteredTypes = if (searchText.isEmpty()) {
            availableTypes
        } else {
            val matcher = NameUtil.buildMatcher("*$searchText*", NameUtil.MatchingCaseSensitivity.NONE)
            availableTypes.filter { resourceType -> matcher.matches(resourceType) }
        }

        filteredTypes.forEach { type ->
            val isSelected = type in currentSelections // Use tracked selections
            typesList.addItem(type, type, isSelected)
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        panel.add(searchField, BorderLayout.NORTH)
        panel.add(JBScrollPane(typesList), BorderLayout.CENTER)
        panel.preferredSize = Dimension(400, 300)

        return panel
    }

    override fun doOKAction() {
        selectedResourceTypes = currentSelections.toList() // Use tracked selections
        super.doOKAction()
    }
}
