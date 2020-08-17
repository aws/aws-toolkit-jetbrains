// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import java.util.*

class QueryEditorSavedState {
    fun setQueryEditorState(queryDetails: QueryDetails, enabledComponentsDisabledComponents: EnabledComponentsState){
        savedState = queryDetails
        enabledDisabledOptionsState = enabledComponentsDisabledComponents
    }

    fun getQueryEditorState() : QueryDetails {
        return savedState
    }

    fun getEnabledDisabledOptionsState(): EnabledComponentsState {
        return enabledDisabledOptionsState
    }

    companion object{
        var savedState = QueryDetails (
            listOf("Default log"),
            false,
            Calendar.getInstance().time
            , Calendar.getInstance().time,
            true,
            "Minutes",
            "10",
            true,
            "Error",
            false,
            "fields @timestamp, @message\n" +
                "| sort @timestamp desc\n" +
                "| limit 20"

            )
        var enabledDisabledOptionsState  = EnabledComponentsState(
            false,
            false,
            true,
            true,
            true,
            false)
    }
}
