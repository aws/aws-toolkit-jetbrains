// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.util.xmlb.annotations.Tag
import java.time.Instant
import java.util.Date

@Tag("SelectedLogGroups")
data class SelectedLogGroups(
    var logGroups: String? = null
)

data class QueryDetails(
    val logGroupName: String,
    val absoluteTimeSelected: Boolean,
    val startDateAbsolute: Date,
    val endDateAbsolute: Date,
    val relativeTimeSelected: Boolean,
    val relativeTimeUnit: String,
    val relativeTimeNumber: String,
    val searchTermSelected: Boolean,
    val searchTerm: String,
    val queryingLogsSelected: Boolean,
    val query: String
)

data class StartEndDate(
    val startDate: Instant,
    val endDate: Instant
)
