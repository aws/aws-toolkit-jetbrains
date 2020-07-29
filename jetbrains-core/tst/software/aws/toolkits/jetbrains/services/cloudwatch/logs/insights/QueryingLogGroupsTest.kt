// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import org.assertj.core.api.Assertions.assertThat
import software.aws.toolkits.resources.message
import java.util.Calendar
import java.util.Date

@RunsInEdt
class QueryingLogGroupsTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()
    private val validator = QueryEditorValidator
    private lateinit var view: QueryEditor

    @Before
    fun setup() {
        val project = projectRule.project
        runInEdtAndWait {
            view = QueryEditor(project)
        }
    }

    @Test
    fun `Absolute or relative time selected`() {
        getViewDetails(absoluteTime = false, relativeTime = false)
        assertThat(validator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.validation.timerange"))
    }

    @Test
    fun `Start date must be before end date`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        getViewDetails(absoluteTime = true, startDate = Calendar.getInstance().time, endDate = cal.time)
        assertThat(validator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.compare.start.end.date"))
    }

    @Test
    fun `Relative Time, no time entered`() {
        getViewDetails(relativeTime = true, relativeTimeNumber = "")
        assertThat(validator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.no_relative_time_number"))
    }

    @Test
    fun `Neither Search Term nor Querying through log groups selected`() {
        getViewDetails(relativeTime = true)
        assertThat(validator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.no_query_selected"))
    }

    @Test
    fun `No search term entered`() {
        getViewDetails(relativeTime = true, querySearch = true, searchTerm = "")
        assertThat(validator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.no_term_entered"))
    }

    @Test
    fun `No query entered`() {
        getViewDetails(relativeTime = true, queryLogs = true, query = "")
        assertThat(validator.validateEditorEntries(view)?.message).contains(message("cloudwatch.logs.no_query_entered"))
    }

    @Test
    fun `All form entries validated Variant1`() {
        getViewDetails(relativeTime = true, queryLogs = true, query = "fields @timestamp")
        assertThat(validator.validateEditorEntries(view)?.message).isNull()
    }

    @Test
    fun `All form entries validated Variant2`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        getViewDetails(absoluteTime = true, endDate = Calendar.getInstance().time, startDate = cal.time, querySearch = true, searchTerm = "Error")
        assertThat(validator.validateEditorEntries(view)?.message).isNull()
    }

    private fun getViewDetails(
        absoluteTime: Boolean = false,
        relativeTime: Boolean = false,
        startDate: Date = Calendar.getInstance().time,
        endDate: Date = Calendar.getInstance().time,
        relativeTimeUnit: String = "Minutes",
        relativeTimeNumber: String = "1",
        querySearch: Boolean = false,
        queryLogs: Boolean = false,
        searchTerm: String = "Example",
        query: String = "Example Query"
    ) {
        view.relativeTimeRadioButton.isSelected = relativeTime
        view.endDate.date = endDate
        view.startDate.date = startDate
        view.absoluteTimeRadioButton.isSelected = absoluteTime
        view.relativeTimeUnit.selectedItem = relativeTimeUnit
        view.relativeTimeNumber.text = relativeTimeNumber
        view.queryLogGroupsRadioButton.isSelected = queryLogs
        view.searchTerm.isSelected = querySearch
        view.querySearchTerm.text = searchTerm
        view.queryBox.text = query
    }
}
