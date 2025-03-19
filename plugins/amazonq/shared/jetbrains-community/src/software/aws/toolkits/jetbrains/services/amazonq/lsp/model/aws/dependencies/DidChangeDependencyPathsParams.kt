// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies

class DidChangeDependencyPathsParams(
    val moduleName: String,
    val programmingLanguage: String,
    val files: List<String>,
    val dirs: List<String>,
    val includePatterns: List<String>,
    val excludePatterns: List<String>,
)
