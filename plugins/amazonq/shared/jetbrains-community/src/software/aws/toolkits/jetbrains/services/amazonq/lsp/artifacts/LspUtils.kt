// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import java.nio.file.Paths

fun getToolkitsCommonCacheRoot(): Path = when {
    SystemInfo.isWindows -> {
        Paths.get(System.getenv("LOCALAPPDATA"))
    }
    SystemInfo.isMac -> {
        Paths.get(System.getProperty("user.home"), "Library", "Caches")
    }
    else -> {
        Paths.get(System.getProperty("user.home"), ".cache")
    }
}

fun getCurrentOS(): String = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isMac -> "darwin"
    else -> "linux"
}

fun getCurrentArchitecture() = when {
    CpuArch.CURRENT == CpuArch.X86_64 -> "x64"
    else -> "arm64"
}

fun generateMD5Hash(filePath: Path): String {
    val messageDigest = DigestUtil.md5()
    DigestUtil.updateContentHash(messageDigest, filePath)
    return StringUtil.toHexString(messageDigest.digest())
}
