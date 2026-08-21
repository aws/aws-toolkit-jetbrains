// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import java.nio.file.Path
import java.nio.file.Paths

internal fun getAwsCacheRoot(): Path = when {
    SystemInfo.isWindows -> Paths.get(System.getenv("LOCALAPPDATA"))
    SystemInfo.isMac -> Paths.get(System.getProperty("user.home"), "Library", "Caches")
    else -> Paths.get(System.getProperty("user.home"), ".cache")
}.resolve("aws")

internal object PlatformResolver {
    private val LOG = getLogger<PlatformResolver>()

    enum class Platform(val value: String) {
        Win32("win32"),
        Darwin("darwin"),
        Linux("linux"),
        LegacyLinux("linuxglib2.28")
    }

    enum class Aarch(val value: String) {
        Arm64("arm64"),
        X64("x64"),
    }

    fun resolvePlatform(): Platform = when {
        SystemInfo.isWindows -> Platform.Win32

        SystemInfo.isMac -> Platform.Darwin

        LegacyLinuxDetector.isLegacyLinux() -> {
            LOG.info { "Legacy linux environment detected, using linuxglib2.28 builds" }
            Platform.LegacyLinux
        }

        else -> Platform.Linux
    }

    fun resolveArchitecture(): Aarch = when (CpuArch.CURRENT) {
        CpuArch.ARM32,
        CpuArch.ARM64,
            -> Aarch.Arm64

        else -> Aarch.X64
    }
}
