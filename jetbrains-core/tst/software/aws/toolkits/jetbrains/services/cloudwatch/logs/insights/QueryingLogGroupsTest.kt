// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import org.assertj.core.api.Assertions.assertThat
import software.aws.toolkits.resources.message
import java.util.Calendar

@RunsInEdt
class QueryingLogGroupsTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()
    private val qEditorValidator = QueryEditorValidator
    private lateinit var view: QueryEditor

    @Before
    fun setup() {
        val project = projectRule.project
        val sdk = IdeaTestUtil.getMockJdk18()
        runInEdtAndWait {
            runWriteAction {
                ProjectJdkTable.getInstance().addJdk(sdk, projectRule.fixture.projectDisposable)
                ProjectRootManager.getInstance(projectRule.project).projectSdk = sdk
            }
            view = QueryEditor(project)
        }
    }

    @Test
    fun absoluteOrRelativeTimeSelected() {
        view.absoluteTimeRadioButton.isSelected = false
        view.relativeTimeRadioButton.isSelected = false
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.validation.timerange"))
    }

    @Test
    fun startDateNotSelected() {
        view.absoluteTimeRadioButton.isSelected = true
        view.StartDate.date = null
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Start Date must be specified")
    }

    @Test
    fun endDateNotSelected() {
        view.absoluteTimeRadioButton.isSelected = true
        view.StartDate.date = Calendar.getInstance().time
        view.EndDate.date = null
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("End Date must be specified")
    }

    @Test
    fun startDateBeforeEndDate() {
        view.absoluteTimeRadioButton.isSelected = true
        view.StartDate.date = Calendar.getInstance().time
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        view.EndDate.date = cal.time
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Start date must be before End date")
    }

    @Test
    fun relativeTimeUnitSelected() {
        view.relativeTimeRadioButton.isSelected = true
        view.relativeTimeUnit.selectedItem = "Minutes"
        view.relativeTimeNumber.text = ""
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Number must be specified")
    }

    @Test
    fun searchOrQuerySelected() {
        view.relativeTimeRadioButton.isSelected = true
        view.StartDate.date = null
        view.EndDate.date = null
        view.absoluteTimeRadioButton.isSelected = false
        view.relativeTimeUnit.selectedItem = "Minutes"
        view.relativeTimeNumber.text = "1"
        view.queryLogGroupsRadioButton.isSelected = false
        view.searchTerm.isSelected = false
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Query must be entered")
    }

    @Test
    fun searchTermSpecified() {
        view.relativeTimeRadioButton.isSelected = true
        view.StartDate.date = null
        view.EndDate.date = null
        view.absoluteTimeRadioButton.isSelected = false
        view.relativeTimeUnit.selectedItem = "Minutes"
        view.relativeTimeNumber.text = "1"
        view.queryLogGroupsRadioButton.isSelected = false
        view.searchTerm.isSelected = true
        view.querySearchTerm.text = ""
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Search Term must be specified")
    }

    @Test
    fun querySpecified() {
        view.relativeTimeRadioButton.isSelected = true
        view.StartDate.date = null
        view.EndDate.date = null
        view.absoluteTimeRadioButton.isSelected = false
        view.relativeTimeUnit.selectedItem = "Minutes"
        view.relativeTimeNumber.text = "1"
        view.queryLogGroupsRadioButton.isSelected = true
        view.searchTerm.isSelected = false
        view.queryBox.text = ""
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).contains("Query must be specified")
    }

    @Test
    fun completePath1() {
        view.relativeTimeRadioButton.isSelected = true
        view.StartDate.date = null
        view.EndDate.date = null
        view.absoluteTimeRadioButton.isSelected = false
        view.relativeTimeUnit.selectedItem = "Minutes"
        view.relativeTimeNumber.text = "1"
        view.queryLogGroupsRadioButton.isSelected = true
        view.searchTerm.isSelected = false
        view.queryBox.text = "fields @timestamp"
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).isNull()
    }

    @Test
    fun completePath2() {
        view.relativeTimeRadioButton.isSelected = false
        view.EndDate.date = Calendar.getInstance().time
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        view.StartDate.date = cal.time
        view.absoluteTimeRadioButton.isSelected = true
        view.relativeTimeUnit.selectedItem = "Minutes"
        view.relativeTimeNumber.text = ""
        view.queryLogGroupsRadioButton.isSelected = false
        view.searchTerm.isSelected = true
        view.querySearchTerm.text = "Error"
        assertThat(qEditorValidator.validateEditorEntries(view)?.message).isNull()
    }
}
