// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

sealed class DownloadArtifactResult {
    data class Success(val artifact: CodeModernizerArtifact, val zipPath: String) : DownloadArtifactResult()
    data class Failure(val errorMessage: String) : DownloadArtifactResult()
    data class DownloadFailure(val failureReason: DownloadFailureReason) : DownloadArtifactResult()
}
