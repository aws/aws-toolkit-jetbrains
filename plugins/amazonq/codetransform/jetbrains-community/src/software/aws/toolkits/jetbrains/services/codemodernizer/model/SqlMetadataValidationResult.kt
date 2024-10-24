// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

data class SqlMetadataValidationResult(
    val valid: Boolean,
    val errorReason: String = "",
    val sourceVendor: String = "",
    val targetVendor: String = "",
    val sourceServerName: String = "",
    val schemaOptions: Set<String> = emptySet(),
)
