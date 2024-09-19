// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import java.io.File

sealed interface MavenCopyCommandsResult {
    data class Success(val dependencyDirectory: File) : MavenCopyCommandsResult
    data object Failure : MavenCopyCommandsResult
    data object Cancelled : MavenCopyCommandsResult
    data object NoJdk : MavenCopyCommandsResult
}
