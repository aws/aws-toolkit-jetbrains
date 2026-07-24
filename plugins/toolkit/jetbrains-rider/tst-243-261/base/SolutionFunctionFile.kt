// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

import com.intellij.openapi.project.Project
import com.jetbrains.rider.projectView.solutionDirectory
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath

// Pre-2026.2: getVirtualFileFromPath's base-dir parameter is a java.io.File, which is what solutionDirectory returns.
internal fun solutionFunctionFile(project: Project) =
    getVirtualFileFromPath("src/HelloWorld/Function.cs", project.solutionDirectory)
