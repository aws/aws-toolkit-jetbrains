// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeModernizerManifest(val version: Float, val patchesRoot: String, val artifactsRoot: String, val summaryRoot: String, val metricsRoot: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PatchInfo(val name: String, val filename: String, val isSuccessful: Boolean)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DescriptionContent(val content: List<PatchInfo>)
