// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.annotations.SerializedName

data class AmazonQLspConfiguration(
    @SerializedName(AmazonQLspConstants.LSP_OPT_OUT_TELEMETRY_CONFIGURATION_KEY)
    val optOutTelemetry: Boolean? = null,

    @SerializedName(AmazonQLspConstants.LSP_ENABLE_TELEMETRY_EVENTS_CONFIGURATION_KEY)
    val enableTelemetryEvents: Boolean? = null,

    @SerializedName(AmazonQLspConstants.LSP_CUSTOMIZATION_CONFIGURATION_KEY)
    val customization: String? = null,
)
