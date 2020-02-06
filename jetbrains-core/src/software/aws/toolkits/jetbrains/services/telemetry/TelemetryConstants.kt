// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

object TelemetryConstants {
    enum class TelemetryResult {
        Succeeded,
        Failed,
        Cancelled
    }

    const val RESULT = "result"

    const val CLOUDDEBUG_WORKFLOWTOKEN = "workflowToken"
}
