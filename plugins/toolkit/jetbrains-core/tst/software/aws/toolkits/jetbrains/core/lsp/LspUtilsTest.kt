// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class LspUtilsTest {

    @Test
    fun `getToolkitsCacheRoot returns platform-appropriate path`() {
        val cacheRoot = getToolkitsCacheRoot()

        assertThat(cacheRoot.toString()).contains("aws")
        assertThat(cacheRoot.toString()).contains("toolkits")

        when {
            SystemInfo.isMac -> assertThat(cacheRoot.toString()).contains("Library/Caches")
            SystemInfo.isWindows -> assertThat(cacheRoot.toString()).doesNotContain(".cache")
            else -> assertThat(cacheRoot.toString()).contains(".cache")
        }
    }

    @Test
    fun `getCurrentOS returns correct platform string`() {
        val os = getCurrentOS()

        when {
            SystemInfo.isWindows -> assertThat(os).isEqualTo("windows")
            SystemInfo.isMac -> assertThat(os).isEqualTo("darwin")
            else -> assertThat(os).isEqualTo("linux")
        }
    }

    @Test
    fun `getCurrentArchitecture returns correct architecture string`() {
        val arch = getCurrentArchitecture()

        when (CpuArch.CURRENT) {
            CpuArch.X86_64 -> assertThat(arch).isEqualTo("x64")
            CpuArch.ARM64 -> assertThat(arch).isEqualTo("arm64")
            else -> assertThat(arch).isEqualTo("unknown")
        }
    }
}
