// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import java.nio.file.Paths

internal fun getToolkitsCacheRoot(): Path = when {
    SystemInfo.isWindows -> Paths.get(System.getenv("LOCALAPPDATA"))
    SystemInfo.isMac -> Paths.get(System.getProperty("user.home"), "Library", "Caches")
    else -> Paths.get(System.getProperty("user.home"), ".cache")
}.resolve("aws").resolve("toolkits")

internal fun getCurrentOS(): String = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isMac -> "darwin"
    else -> "linux"
}

internal fun getCurrentArchitecture(): String = when (CpuArch.CURRENT) {
    CpuArch.X86_64 -> "x64"
    CpuArch.ARM64 -> "arm64"
    else -> "unknown"
}
