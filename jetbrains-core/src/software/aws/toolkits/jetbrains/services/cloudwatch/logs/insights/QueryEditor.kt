// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.michaelbaranov.microba.calendar.DatePicker
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.utils.ui.find
import software.aws.toolkits.resources.message
import java.text.NumberFormat
import java.time.temporal.ChronoUnit
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFormattedTextField
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextArea
import javax.swing.JTextField

class QueryEditor internal constructor(
    private val project: Project,
    val initialQueryDetails: QueryDetails
) {
    lateinit var absoluteTimeRadioButton: JRadioButton
    lateinit var relativeTimeRadioButton: JRadioButton
    lateinit var searchTerm: JRadioButton
    lateinit var querySearchTerm: JTextField
    lateinit var queryLogGroupsRadioButton: JRadioButton
    lateinit var saveQueryButton: JButton
    lateinit var retrieveSavedQueriesButton: JButton
    lateinit var tablePanel: SimpleToolWindowPanel
    lateinit var queryBox: JTextArea
    lateinit var endDate: DatePicker
    lateinit var queryEditorBasePanel: JPanel
    private lateinit var comboBoxModel: EnumComboBoxModel<TimeUnit>
    lateinit var relativeTimeUnit: JComboBox<TimeUnit>
    private lateinit var numberFormat: NumberFormat
    lateinit var relativeTimeNumber: JFormattedTextField
    lateinit var startDate: DatePicker
    lateinit var queryGroupScrollPane: JBScrollPane
    lateinit var logGroupTable: LogGroupSelectorTable
    private lateinit var timePanel: JPanel
    private lateinit var searchPanel: JPanel

    private fun createUIComponents() {
        tablePanel = SimpleToolWindowPanel(false, true)
        logGroupTable = LogGroupSelectorTable()
        tablePanel.setContent(logGroupTable.component)
        // lateinit since this method runs before the standard initialization flow
        numberFormat = NumberFormat.getIntegerInstance()
        relativeTimeNumber = JFormattedTextField(numberFormat)
        // arbitrary length
        relativeTimeNumber.columns = 5
        comboBoxModel = EnumComboBoxModel(TimeUnit::class.java)
        relativeTimeUnit = ComboBox(comboBoxModel)
        relativeTimeUnit.renderer = timeUnitComboBoxRenderer
    }

    init {
        absoluteTimeRadioButton.addActionListener {
            setAbsolute()
        }

        relativeTimeRadioButton.addActionListener {
            setRelative()
        }

        queryLogGroupsRadioButton.addActionListener {
            setQueryLanguage()
        }

        searchTerm.addActionListener {
            setSearchTerm()
        }

        saveQueryButton.addActionListener {
            val query = if (queryBox.text.isNotEmpty()) queryBox.text else DEFAULT_INSIGHTS_QUERY_STRING
            SaveQueryDialog(project, initialQueryDetails.connectionSettings, query, logGroupTable.getSelectedLogGroups()).show()
        }

        retrieveSavedQueriesButton.addActionListener {
            RetrieveSavedQueryDialog(this, project, initialQueryDetails.connectionSettings).show()
        }

        startDate.isEnabled = false
        endDate.isEnabled = false
        relativeTimeNumber.isEnabled = true
        relativeTimeUnit.isEnabled = true
        querySearchTerm.isEnabled = true
        queryBox.isEnabled = false
        saveQueryButton.isEnabled = false
        queryLogGroupsRadioButton.isSelected = true
        queryBox.text = DEFAULT_INSIGHTS_QUERY_STRING

        queryGroupScrollPane.border = IdeBorderFactory.createTitledBorder(message("cloudwatch.logs.log_groups"), false, JBUI.emptyInsets())
        timePanel.border = JBUI.Borders.emptyTop(UIUtil.DEFAULT_VGAP)
        searchPanel.border = JBUI.Borders.emptyTop(UIUtil.DEFAULT_VGAP)
    }

    fun setAbsolute() {
        absoluteTimeRadioButton.isSelected = true
        startDate.isEnabled = true
        endDate.isEnabled = true
        relativeTimeNumber.isEnabled = false
        relativeTimeUnit.isEnabled = false
    }

    fun setRelative() {
        relativeTimeRadioButton.isSelected = true
        startDate.isEnabled = false
        endDate.isEnabled = false
        relativeTimeNumber.isEnabled = true
        relativeTimeUnit.isEnabled = true
    }

    fun setSearchTerm() {
        searchTerm.isSelected = true
        queryBox.isEnabled = false
        querySearchTerm.isEnabled = true
        saveQueryButton.isEnabled = false
    }

    fun setQueryLanguage() {
        queryLogGroupsRadioButton.isEnabled = true
        queryBox.isEnabled = true
        querySearchTerm.isEnabled = false
        saveQueryButton.isEnabled = true
    }

    fun getRelativeTimeAmount() = numberFormat.parse(relativeTimeNumber.text).toLong()

    fun getSelectedTimeUnit(): ChronoUnit = comboBoxModel.selectedItem.unit

    fun setSelectedTimeUnit(unit: ChronoUnit) {
        comboBoxModel.setSelectedItem(comboBoxModel.find { it.unit == unit })
    }

    companion object {
        private val LOG = getLogger<QueryEditor>()

        private val timeUnitComboBoxRenderer = SimpleListCellRenderer.create<TimeUnit>("") {
            it.text
        }
    }
}
