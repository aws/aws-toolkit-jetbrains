// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import java.util.Calendar

class QueryEditorSavedState {
    fun setQueryEditorState(queryDetails: QueryDetails, enabledComponentsDisabledComponents: EnabledComponentsState) {
        currentQueryEditorState = queryDetails
        enabledDisabledOptionsState = enabledComponentsDisabledComponents
    }

    fun getQueryEditorState(): QueryDetails = currentQueryEditorState

    fun getEnabledDisabledOptionsState(): EnabledComponentsState = enabledDisabledOptionsState

    companion object {
        var currentQueryEditorState = QueryDetails(
            listOf("Default log"),
            false,
            Calendar.getInstance().time,
            Calendar.getInstance().time,
            true,
            "Minutes",
            "10",
            true,
            "Error",
            false,
            default_query
            )

        var initialQueryEditorState = QueryDetails(
            listOf("Default log"),
            false,
            Calendar.getInstance().time,
            Calendar.getInstance().time,
            true,
            "Minutes",
            "10",
            true,
            "Error",
            false,
            default_query
        )

        var enabledDisabledOptionsState = EnabledComponentsState(
            startDateEnabled = false,
            endDateEnabled = false,
            relativeTimeNumberEnabled = true,
            relativeTimeUnitEnabled = true,
            querySearchTermEnabled = true,
            queryBoxEnabled = false
        )
        var initialEnabledDisabledOptionsState = EnabledComponentsState(
            startDateEnabled = false,
            endDateEnabled = false,
            relativeTimeNumberEnabled = true,
            relativeTimeUnitEnabled = true,
            querySearchTermEnabled = true,
            queryBoxEnabled = false
        )
    }
}
