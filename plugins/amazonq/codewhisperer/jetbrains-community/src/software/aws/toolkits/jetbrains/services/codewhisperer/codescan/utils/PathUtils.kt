// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils

import java.nio.file.Path

object PathUtils {
    fun getNormalizedRelativePath(projectName: String, relativePath: Path): String =
        "$projectName/${relativePath.normalize()}"
}
