// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

import com.intellij.openapi.project.Project
import com.jetbrains.rider.projectView.solutionDirectoryPath
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath

// 2026.2+: getVirtualFileFromPath's base-dir parameter is a java.nio.file.Path (was java.io.File before).
internal fun solutionFunctionFile(project: Project) =
    getVirtualFileFromPath("src/HelloWorld/Function.cs", project.solutionDirectoryPath)
