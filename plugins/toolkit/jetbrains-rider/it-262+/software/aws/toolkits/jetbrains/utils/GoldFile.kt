// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.project.Project
import com.jetbrains.rider.projectView.solutionDirectoryPath

// 2026.2+: debugProgramAfterAttach's gold-file parameter is a java.nio.file.Path (was java.io.File before).
internal fun goldFile(project: Project, name: String) = project.solutionDirectoryPath.resolve(name)
