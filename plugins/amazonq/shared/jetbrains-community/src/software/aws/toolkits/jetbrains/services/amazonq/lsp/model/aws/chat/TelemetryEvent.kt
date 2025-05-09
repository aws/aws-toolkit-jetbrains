// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LSPAny

/**
 * Notification for telemetry events
 */
class TelemetryEventNotification(
    override val command: String = TELEMETRY_EVENT,
    override val params: TelemetryEventParams,
) : ChatNotification<TelemetryEventParams>

/**
 * Parameters for telemetry events
 * Using LSPAny to avoid deserialization of different event types
 * Example: {"triggerType":"click","tabId":"z869mz","name":"tabAdd"}
 */
class TelemetryEventParams(
    val value: LSPAny,
)
