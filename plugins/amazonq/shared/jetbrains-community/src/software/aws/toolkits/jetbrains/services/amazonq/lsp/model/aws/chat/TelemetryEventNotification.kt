// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

class TelemetryEventNotification(
    override val command: String = TELEMETRY_EVENT,
    override val params: Map<String, Any?>,
) : ChatNotification<Map<String, Any?>>
