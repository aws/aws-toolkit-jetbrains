// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.Action
import javax.swing.JComponent
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message

class QueryEditorDialog(
    private val project: Project,
    private val lGroupName: String,
    private val absoluteTimeSelected:Boolean=false

): DialogWrapper(project){
    constructor(project: Project,logGroupName: String):
        this(project= project, lGroupName=logGroupName)

    private val view = Queryeditor(project)
    private val qQueryingLogGroupApiCall=QueryingLogGroups(project)
    private val action: OkAction=QueryLogGroupOkAction()
    private val validator=QueryEditorValidator()
    init{
        super.init()

        title="Query Log Groups"

        view.absoluteTimeRadioButton.addActionListener{
            view.qstartDate.setEnabled(true)
            view.qendDate.setEnabled(true)
            view.RelativeTimeNumber.setEnabled(false)
            view.RelativeTimeUnit.setEnabled(false)
        }
        view.relativeTimeRadioButton.addActionListener {
            view.qstartDate.setEnabled(false)
            view.qendDate.setEnabled(false)
            view.RelativeTimeNumber.setEnabled(true)
            view.RelativeTimeUnit.setEnabled(true)
        }
        view.queryLogGroupsRadioButton.addActionListener {
            view.queryBox.setEnabled(true);
            view.querySearchTerm.setEnabled(false);
        }
        view.searchterm.addActionListener {
            view.queryBox.setEnabled(false);
            view.querySearchTerm.setEnabled(true);
        }



    }
    //qpanel is the underlying panel for the Query Editor
    override fun createCenterPanel(): JComponent? = view.qpanel
    override fun doValidate(): ValidationInfo? =validator.validateEditorEntries(view)
    override fun getOKAction(): Action = action
    override fun doCancelAction() {
        super.doCancelAction()
    }

    override fun doOKAction() {
        // Do nothing, close logic is handled separately
    }

    private fun getRelativeTime(unitOfTime:String,relTimeNumber:Long):StartEndDate{

        val enddate=System.currentTimeMillis()/1000
        val startdate:Long=when(unitOfTime){
            "Minutes"->enddate-(relTimeNumber*60)
            "Hours"->enddate-(relTimeNumber*60*60)
            "Days"->enddate-(relTimeNumber*60*60*24)
            "Week"->enddate-(relTimeNumber*60*60*24*7)
            else -> 0
        }
        val startend =StartEndDate(startdate,enddate)
        return startend

    }
    private fun getAbsoluteTime(startDate:Date,endDate:Date):StartEndDate=StartEndDate(((startDate.time)/1000),((endDate.time)/1000))

    public fun getFilterQuery(searchTerm:String):String= "fields @message, @timestamp | filter @message like '$searchTerm'"

    private fun beginQuerying(){
        if (!okAction.isEnabled) {
            return
        }
        val funDetails=getFunctionDetails()
        val queryStartDate:Long
        val queryEndDate:Long
        val queryStartEndDate:StartEndDate
        queryStartEndDate = if(funDetails.absoluteTimeSelected){
            getAbsoluteTime(funDetails.qStartDateAbsolute!!, funDetails.qEndDateAbsolute!!)
        }
        else{
            getRelativeTime(funDetails.qRelativeTimeUnit,funDetails.qRelativeTimeNumber.toLong())
        }
        queryStartDate=queryStartEndDate.startDate
        queryEndDate=queryStartEndDate.endDate

        val query = if(funDetails.queryingLogsSelected){
            funDetails.qQuery }
        else{
            getFilterQuery(funDetails.qSearchTerm)

        }
        close(OK_EXIT_CODE)
        qQueryingLogGroupApiCall.executeStartQuery(queryEndDate,funDetails.logGroupName,query,queryStartDate)

    }
    private fun getFunctionDetails(): QueryDetails=QueryDetails(
        logGroupName=lGroupName,
        absoluteTimeSelected=view.absoluteTimeRadioButton.isSelected,
        qStartDateAbsolute=view.qstartDate.date,
        qEndDateAbsolute=view.qendDate.date,
        relativeTimeSelected=view.relativeTimeRadioButton.isSelected,
        qRelativeTimeUnit=view.RelativeTimeUnit.selectedItem.toString(),
        qRelativeTimeNumber=view.RelativeTimeNumber.text,
        searchTermSelected=view.searchterm.isSelected,
        qSearchTerm=view.querySearchTerm.text,
        queryingLogsSelected=view.queryLogGroupsRadioButton.isSelected,
        qQuery=view.queryBox.text
    )

    private inner class QueryLogGroupOkAction:OkAction(){
        init {
            putValue(Action.NAME,"Apply")
        }
        override fun doAction(e: ActionEvent?) {
            super.doAction(e)
            if (doValidateAll().isNotEmpty()) return
            beginQuerying()
        }
    }
}

class QueryEditorValidator{
    fun validateEditorEntries(view:Queryeditor):ValidationInfo?{
        if (!view.absoluteTimeRadioButton.isSelected && !view.relativeTimeRadioButton.isSelected){
        return ValidationInfo(message("cloudwatch.logs.validation.timerange"),view.absoluteTimeRadioButton)}
        if (view.absoluteTimeRadioButton.isSelected && view.qstartDate.date==null){return ValidationInfo("Start Date must be specified",view.qstartDate)}
        if (view.absoluteTimeRadioButton.isSelected && view.qendDate.date==null){return ValidationInfo("End Date must be specified",view.qendDate)}
        if (view.relativeTimeRadioButton.isSelected && view.RelativeTimeNumber.text.isEmpty()){return ValidationInfo("Number must be specified",view.RelativeTimeNumber)}
        if(view.absoluteTimeRadioButton.isSelected && view.qstartDate.date>view.qendDate.date){return ValidationInfo("Start date must be before End date",view.qstartDate)}
        if (!view.queryLogGroupsRadioButton.isSelected && !view.searchterm.isSelected){
            return ValidationInfo("Query must be entered",view.searchterm)}
        if(view.queryLogGroupsRadioButton.isSelected && view.queryBox.text.isEmpty()){
            return ValidationInfo("Query must be specified",view.queryBox)
        }
        if(view.searchterm.isSelected && view.querySearchTerm.text.isEmpty()){
            return ValidationInfo("Search Term must be specified",view.querySearchTerm)
        }
        return null
    }
}

