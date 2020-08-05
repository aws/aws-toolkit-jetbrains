// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.util.ui.ColumnInfo
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField

class ColumnInfoDetails(fieldName: String) : ColumnInfo<GetQueryResultsResponse, String>(fieldName) {
    override fun valueOf(item: GetQueryResultsResponse?): String? {
        item?.results()
        return "a"
    }

}
