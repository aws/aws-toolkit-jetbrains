// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class LspUtilsTest {
    @Test
    fun `PlatformResolver returns known architecture`() {
        assertThat(PlatformResolver.resolveArchitecture()).isIn(PlatformResolver.Aarch.Arm64, PlatformResolver.Aarch.X64)
    }

    @Test
    fun `PlatformResolver returns known platform`() {
        val platform = PlatformResolver.resolvePlatform()

        when {
            SystemInfo.isWindows -> assertThat(platform).isEqualTo(PlatformResolver.Platform.Win32)
            SystemInfo.isMac -> assertThat(platform).isEqualTo(PlatformResolver.Platform.Darwin)
            else -> assertThat(platform).isIn(PlatformResolver.Platform.Linux, PlatformResolver.Platform.LegacyLinux)
        }
    }

    @Test
    fun `win32 platform uses manifest literal`() {
        assertThat(PlatformResolver.Platform.Win32.value).isEqualTo("win32")
    }
}
