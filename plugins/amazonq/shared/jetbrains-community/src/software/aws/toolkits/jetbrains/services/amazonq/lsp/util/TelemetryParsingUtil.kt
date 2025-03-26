// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit

object TelemetryParsingUtil {

    fun parseMetricUnit(value: Any?): MetricUnit {
        return when (value) {
            is String -> MetricUnit.fromValue(value) ?: MetricUnit.NONE
            is MetricUnit -> value
            else -> MetricUnit.NONE
        }
    }
}
