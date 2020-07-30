// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import java.time.Instant
import java.util.Date

data class SelectedLogGroups(
    var logGroups: String? = TODO()
)

data class QueryDetails(
    val logGroupName: List<String>,
    val absoluteTimeSelected: Boolean,
    val startDateAbsolute: Date,
    val endDateAbsolute: Date,
    val relativeTimeSelected: Boolean,
    val relativeTimeUnit: String,
    val relativeTimeNumber: String,
    val searchTermSelected: Boolean,
    val searchTerm: String,
    val enterQuery: Boolean,
    val query: String
)

data class StartEndDate(
    val startDate: Instant,
    val endDate: Instant
)
