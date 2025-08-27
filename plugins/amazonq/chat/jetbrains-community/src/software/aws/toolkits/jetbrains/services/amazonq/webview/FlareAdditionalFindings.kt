// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// TODO: move to software.aws.toolkits.jetbrains.services.amazonq.lsp.model
package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.Description
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.Recommendation
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.SuggestedFix

/**
 * Solely used to extract the aggregated findings from the response
 */
data class FlareAdditionalMessages(
    @get:JsonDeserialize(contentUsing = FlareAggregatedFindingsDeserializer::class)
    @get:JsonSetter(contentNulls = Nulls.SKIP)
    val additionalMessages: List<FlareAggregatedFindings>?,
)

data class FlareAggregatedFindings(
    val messageId: String,
    val body: List<AggregatedCodeScanIssue>,
)

data class FlareCodeScanIssue(
    val startLine: Int,
    val endLine: Int,
    val comment: String?,
    val title: String,
    val description: Description,
    val detectorId: String,
    val detectorName: String,
    val findingId: String,
    val ruleId: String?,
    val relatedVulnerabilities: List<String>,
    val severity: String,
    val recommendation: Recommendation,
    val suggestedFixes: List<SuggestedFix>,
    val scanJobId: String,
    val language: String,
    val autoDetected: Boolean,
    val filePath: String,
    val findingContext: String,
)

data class AggregatedCodeScanIssue(
    val filePath: String,
    val issues: List<FlareCodeScanIssue>,
)

class FlareAggregatedFindingsDeserializer : JsonDeserializer<FlareAggregatedFindings>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FlareAggregatedFindings? =
        // drop values that do not look like FlareAggregatedFindings
        try {
            ctxt.readValue(p, FlareAggregatedFindings::class.java)
        } catch (_: Exception) {
            null
        }
}
