// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.util

import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService

data class HighlightCommand(val command: String, val description: String)

fun highlightCommand(): HighlightCommand? {
    val feature = CodeWhispererFeatureConfigService.getInstance().getHighlightCommandFeature()

    if (feature == null || feature.value.stringValue().isEmpty()) return null

    return HighlightCommand(feature.value.stringValue(), feature.variation)
}
