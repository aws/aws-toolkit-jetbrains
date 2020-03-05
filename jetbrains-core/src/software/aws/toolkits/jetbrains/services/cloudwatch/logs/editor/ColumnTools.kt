// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.util.ui.ColumnInfo
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.resources.message
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CloudWatchLogsStreamsColumn : ColumnInfo<LogStream, String>(message("cloudwatch.logs.log_streams")) {
    override fun valueOf(item: LogStream?): String? = item?.logStreamName()
}

class CloudWatchLogsStreamsColumnDate : ColumnInfo<LogStream, String>(message("general.time")) {
    override fun valueOf(item: LogStream?): String? {
        item ?: return null
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(item.lastEventTimestamp()).atOffset(ZoneOffset.UTC))
    }
}

