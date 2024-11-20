// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeModernizerMetrics(
    val linesOfCodeChanged: Int?,
    val charactersOfCodeChanged: Int?,
    var linesOfCodeSubmitted: Int?,
    var programmingLanguage: String?,
)
