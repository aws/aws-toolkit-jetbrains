// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import org.assertj.core.api.Assertions.assertThat
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message
import java.util.*

@RunsInEdt
class QueryingLogGroupsTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()
    private val qEditorValidator=QueryEditorValidator()

    private lateinit var view: Queryeditor

    @Before
    fun setup() {
        val project=projectRule.project
        val sdk = IdeaTestUtil.getMockJdk18()
        runInEdtAndWait {
            runWriteAction {
                ProjectJdkTable.getInstance().addJdk(sdk, projectRule.fixture.projectDisposable)
                ProjectRootManager.getInstance(projectRule.project).projectSdk = sdk
            }
            view=Queryeditor(project)

        }


    }
    @Test
    fun absoluteOrRelativeTimeSelected(){
        view.absoluteTimeRadioButton.isSelected=false
        view.relativeTimeRadioButton.isSelected=false
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.validation.timerange"))
    }

    @Test
    fun startDateNotSelected(){
        view.absoluteTimeRadioButton.isSelected=true
        view.qstartDate.date=null
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Start Date must be specified")
    }

    @Test
    fun endDateNotSelected(){
        view.absoluteTimeRadioButton.isSelected=true
        view.qstartDate.date= Calendar.getInstance().time
        view.qendDate.date=null
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("End Date must be specified")
    }

    @Test
    fun startDateBeforeEndDate(){
        view.absoluteTimeRadioButton.isSelected=true
        view.qstartDate.date= Calendar.getInstance().time
        val cal=Calendar.getInstance()
        cal.add(Calendar.DATE,-1)
        view.qendDate.date=cal.time
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Start date must be before End date")
    }

    @Test
    fun relativeTimeUnitSelected(){
        view.relativeTimeRadioButton.isSelected=true
        view.RelativeTimeUnit.selectedItem="Minutes"
        view.RelativeTimeNumber.text=""
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Number must be specified")
    }


    @Test
    fun searchOrQuerySelected(){
        view.relativeTimeRadioButton.isSelected=true
        view.qstartDate.date=null
        view.qendDate.date=null
        view.absoluteTimeRadioButton.isSelected=false
        view.RelativeTimeUnit.selectedItem="Minutes"
        view.RelativeTimeNumber.text="1"
        view.queryLogGroupsRadioButton.isSelected=false
        view.searchterm.isSelected=false
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Query must be entered")
    }

    @Test
    fun searchTermSpecified(){
        view.relativeTimeRadioButton.isSelected=true
        view.qstartDate.date=null
        view.qendDate.date=null
        view.absoluteTimeRadioButton.isSelected=false
        view.RelativeTimeUnit.selectedItem="Minutes"
        view.RelativeTimeNumber.text="1"
        view.queryLogGroupsRadioButton.isSelected=false
        view.searchterm.isSelected=true
        view.querySearchTerm.text=""
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Search Term must be specified")
    }

    @Test
    fun querySpecified(){
        view.relativeTimeRadioButton.isSelected=true
        view.qstartDate.date=null
        view.qendDate.date=null
        view.absoluteTimeRadioButton.isSelected=false
        view.RelativeTimeUnit.selectedItem="Minutes"
        view.RelativeTimeNumber.text="1"
        view.queryLogGroupsRadioButton.isSelected=true
        view.searchterm.isSelected=false
        view.queryBox.text=""
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Query must be specified")
    }


}
