// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import software.aws.toolkits.resources.message
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date

class QueryEditorDialog(
    private val project: Project,
    private val lGroupName: String,
    private val absoluteTimeSelected: Boolean = false

) : DialogWrapper(project) {
    constructor(project: Project, logGroupName: String) :
        this(project = project, lGroupName = logGroupName)

    private val view = QueryEditor(project)
    private val qQueryingLogGroupApiCall = QueryingLogGroups(project)
    private val action: OkAction = QueryLogGroupOkAction()
    private val validator = QueryEditorValidator
    init {
        super.init()
        title = "Query Log Groups"
        view.absoluteTimeRadioButton.addActionListener {
            view.qStartDate.isEnabled = true
            view.qEndDate.isEnabled = true
            view.relativeTimeNumber.isEnabled = false
            view.relativeTimeUnit.setEnabled(false)
        }
        view.relativeTimeRadioButton.addActionListener {
            view.qStartDate.isEnabled = false
            view.qEndDate.isEnabled = false
            view.relativeTimeNumber.isEnabled = true
            view.relativeTimeUnit.setEnabled(true)
        }
        view.queryLogGroupsRadioButton.addActionListener {
            view.queryBox.isEnabled = true
            view.querySearchTerm.isEnabled = false
        }
        view.searchTerm.addActionListener {
            view.queryBox.isEnabled = false
            view.querySearchTerm.isEnabled = true
        }
    }
    override fun createCenterPanel(): JComponent? = view.queryEditorBasePanel
    override fun doValidate(): ValidationInfo? = validator.validateEditorEntries(view)
    override fun getOKAction(): Action = action
    override fun doCancelAction() {
        super.doCancelAction()
    }

    override fun doOKAction() {
        // Do nothing, close logic is handled separately
    }

    private fun getRelativeTime(unitOfTime: String, relTimeNumber: Long): StartEndDate {
        val endDate = Calendar.getInstance().toInstant()
        val startDate = when (unitOfTime) {
            "Minutes" -> endDate.minus(relTimeNumber, ChronoUnit.MINUTES)
            "Hours" -> endDate.minus(relTimeNumber, ChronoUnit.HOURS)
            "Days" -> endDate.minus(relTimeNumber, ChronoUnit.DAYS)
            "Week" -> endDate.minus(relTimeNumber, ChronoUnit.WEEKS)
            else -> endDate
        }
        return StartEndDate(startDate, endDate)
    }
    private fun getAbsoluteTime(startDate: Date, endDate: Date): StartEndDate = StartEndDate(startDate.toInstant(), endDate.toInstant())

    public fun getFilterQuery(searchTerm: String): String {
        if (searchTerm.contains("/")) {
            val regexTerm = searchTerm.replace("/", "\\/")
            return "fields @message, @timestamp | filter @message like /$regexTerm/"
        }
        return "fields @message, @timestamp | filter @message like /$searchTerm/"
    }

    private fun beginQuerying() {
        if (!okAction.isEnabled) {
            return
        }
        val funDetails = getFunctionDetails()
        val queryStartEndDate: StartEndDate
        queryStartEndDate = (if (funDetails.absoluteTimeSelected) {
            getAbsoluteTime(funDetails.qStartDateAbsolute, funDetails.qEndDateAbsolute)
        } else {
            getRelativeTime(funDetails.qRelativeTimeUnit, funDetails.qRelativeTimeNumber.toLong())
        })
        val queryStartDate = queryStartEndDate.startDate.toEpochMilli() / 1000
        val queryEndDate = queryStartEndDate.endDate.toEpochMilli() / 1000
        val query = if (funDetails.queryingLogsSelected) {
            funDetails.qQuery } else {
            getFilterQuery(funDetails.qSearchTerm)
        }
        close(OK_EXIT_CODE)
        qQueryingLogGroupApiCall.executeStartQuery(queryEndDate, funDetails.logGroupName, query, queryStartDate)
    }
    private fun getFunctionDetails(): QueryDetails = QueryDetails(
        logGroupName = lGroupName,
        absoluteTimeSelected = view.absoluteTimeRadioButton.isSelected,
        qStartDateAbsolute = view.qStartDate.date,
        qEndDateAbsolute = view.qEndDate.date,
        relativeTimeSelected = view.relativeTimeRadioButton.isSelected,
        qRelativeTimeUnit = view.relativeTimeUnit.selectedItem.toString(),
        qRelativeTimeNumber = view.relativeTimeNumber.text,
        searchTermSelected = view.searchTerm.isSelected,
        qSearchTerm = view.querySearchTerm.text,
        queryingLogsSelected = view.queryLogGroupsRadioButton.isSelected,
        qQuery = view.queryBox.text
    )

    private inner class QueryLogGroupOkAction : OkAction() {
        init {
            putValue(Action.NAME, "Apply")
        }
        override fun doAction(e: ActionEvent?) {
            super.doAction(e)
            if (doValidateAll().isNotEmpty()) return
            beginQuerying()
        }
    }
}

object QueryEditorValidator {
    fun validateEditorEntries(view: QueryEditor): ValidationInfo? {
        if (!view.absoluteTimeRadioButton.isSelected && !view.relativeTimeRadioButton.isSelected) {
        return ValidationInfo(message("cloudwatch.logs.validation.timerange"), view.absoluteTimeRadioButton) }
        if (view.absoluteTimeRadioButton.isSelected && view.qStartDate.date == null) {
            return ValidationInfo(message("cloudwatch.logs.no_start_date"), view.qStartDate) }
        if (view.absoluteTimeRadioButton.isSelected && view.qEndDate.date == null) {
            return ValidationInfo(message("cloudwatch.logs.no_end_date"), view.qEndDate) }
        if (view.relativeTimeRadioButton.isSelected && view.relativeTimeNumber.text.isEmpty()) {
            return ValidationInfo(message("cloudwatch.logs.no_relative_time_number"), view.relativeTimeNumber) }
        if (view.absoluteTimeRadioButton.isSelected && view.qStartDate.date > view.qEndDate.date) {
            return ValidationInfo(message("cloudwatch.logs.compare.start.end.date"), view.qStartDate) }
        if (!view.queryLogGroupsRadioButton.isSelected && !view.searchTerm.isSelected) {
            return ValidationInfo(message("cloudwatch.logs.no_query_selected"), view.searchTerm) }
        if (view.queryLogGroupsRadioButton.isSelected && view.queryBox.text.isEmpty()) {
            return ValidationInfo(message("cloudwatch.logs.no_query_entered"), view.queryBox)
        }
        if (view.searchTerm.isSelected && view.querySearchTerm.text.isEmpty()) {
            return ValidationInfo(message("cloudwatch.logs.no_term_entered"), view.querySearchTerm)
        }
        return null
    }
}
