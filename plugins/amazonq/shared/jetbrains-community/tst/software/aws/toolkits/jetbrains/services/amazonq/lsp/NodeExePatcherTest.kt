// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.system.CpuArch
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.rules.EnvironmentVariableHelper
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.LspSettings
import java.nio.file.Paths

class NodeExePatcherTest {
    @get:Rule
    val envVarHelper = EnvironmentVariableHelper()

    @get:Rule
    val tempDir = TempDirectory()

    @get:Rule
    val appRule = ApplicationRule()

    @get:Rule
    val mockKRule = MockKRule(this)

    private val pathToNode = Paths.get("/path/to/node").toAbsolutePath().toString()

    @Test
    fun `patches if environment variables are available`() {
        val path = tempDir.newDirectory("vsc-sysroot").toPath().toAbsolutePath()
        val linker = Paths.get(path.toString(), "someSharedLibrary").createFile()

        envVarHelper[NodeExePatcher.GLIBC_LINKER_VAR] = linker.toString()
        envVarHelper[NodeExePatcher.GLIBC_PATH_VAR] = path.toString()

        assertThat(NodeExePatcher.patch(Paths.get("/path/to/node")))
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(linker.toString(), "--library-path", path.toString(), pathToNode))
    }

    @Test
    fun `patches if hardcoded paths exists`() {
        val path = Paths.get(NodeExePatcher.INTERNAL_GLIBC_PATH)
        // too many permission issues otherwise
        assumeTrue(path.exists())

        val linker = Paths.get(if (CpuArch.isArm64()) NodeExePatcher.INTERNAL_AARCH64_LINKER else NodeExePatcher.INTERNAL_X86_64_LINKER)

        assertThat(NodeExePatcher.patch(Paths.get("/path/to/node")))
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(linker.toString(), "--library-path", path.toString(), pathToNode))
    }

    @Test
    fun `noop if no patch available`() {
        assertThat(NodeExePatcher.patch(Paths.get("/path/to/node")))
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(pathToNode))
    }

    @Test
    fun `getNodeRuntimePath prefers patched runtime`() {
        val path = tempDir.newDirectory("vsc-sysroot").toPath().toAbsolutePath()
        val linker = Paths.get(path.toString(), "someSharedLibrary").createFile()
        val fakeNode = tempDir.newFile("fake-node").toPath().toAbsolutePath()

        envVarHelper[NodeExePatcher.GLIBC_LINKER_VAR] = linker.toString()
        envVarHelper[NodeExePatcher.GLIBC_PATH_VAR] = path.toString()

        val cmdlineSlot = slot<GeneralCommandLine>()
        mockkStatic(ExecUtil::class)
        mockkStatic(::getStartUrl)
        every { getStartUrl(any()) } returns "https://start.url"
        every { ExecUtil.execAndGetOutput(capture(cmdlineSlot), any<Int>()) } returns ProcessOutput("v99.0.0", "", 0, false, false)

        assertThat(NodeExePatcher.getNodeRuntimePath(mockk(), fakeNode))
            .isEqualTo(fakeNode)

        assertThat(cmdlineSlot.captured)
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(linker.toString(), "--library-path", path.toString(), fakeNode.toString(), "--version"))
    }

    @Test
    fun `getNodeRuntimePath can run without patching`() {
        val fakeNode = tempDir.newFile("fake-node").toPath().toAbsolutePath()
        val cmdlineSlot = slot<GeneralCommandLine>()
        mockkStatic(ExecUtil::class)
        mockkStatic(::getStartUrl)
        every { getStartUrl(any()) } returns "https://start.url"
        every { ExecUtil.execAndGetOutput(capture(cmdlineSlot), any<Int>()) } returns ProcessOutput("v99.0.0", "", 0, false, false)

        assertThat(NodeExePatcher.getNodeRuntimePath(mockk(), fakeNode))
            .isEqualTo(fakeNode)

        assertThat(cmdlineSlot.captured)
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(fakeNode.toString(), "--version"))
    }

    @Test
    fun `getNodeRuntimePath searches environment if artifact not available`() {
        mockkStatic(ExecUtil::class)
        mockkStatic(::getStartUrl)
        every { getStartUrl(any()) } returns "https://start.url"
        every { ExecUtil.execAndGetOutput(any(), any<Int>()) } returns ProcessOutput("v99.0.0", "", 0, false, false)

        assertThat(NodeExePatcher.getNodeRuntimePath(mockk(), Paths.get(pathToNode)))
            .isNotEqualTo(Paths.get(pathToNode))
    }

    @Test
    fun `getNodeRuntimePath returns user-provided path directly`() {
        // Create a directory path
        val nodeDir = tempDir.newDirectory("nodejs").toPath().toAbsolutePath()

        // Mock LspSettings to return the directory path
        val mockLspSettings = mockk<LspSettings>()
        every { mockLspSettings.getNodeRuntimePath() } returns nodeDir.toString()

        mockkObject(LspSettings.Companion)
        every { LspSettings.getInstance() } returns mockLspSettings

        mockkStatic(ExecUtil::class)
        mockkStatic(::getStartUrl)
        every { getStartUrl(any()) } returns "https://start.url"
        every { ExecUtil.execAndGetOutput(any(), any<Int>()) } returns ProcessOutput("v99.0.0", "", 0, false, false)

        val result = NodeExePatcher.getNodeRuntimePath(mockk(), Paths.get(pathToNode))

        // Should return the user-provided path directly without resolution
        assertThat(result).isEqualTo(nodeDir)
    }

    @Test
    fun `getNodeRuntimePath uses user-provided executable path directly`() {
        // Create a node executable file directly
        val exeName = if (SystemInfo.isWindows) "node.exe" else "node"
        val nodeExe = tempDir.newFile(exeName).toPath().toAbsolutePath()
        nodeExe.toFile().setExecutable(true)

        // Mock LspSettings to return the executable path directly
        val mockLspSettings = mockk<LspSettings>()
        every { mockLspSettings.getNodeRuntimePath() } returns nodeExe.toString()

        mockkObject(LspSettings.Companion)
        every { LspSettings.getInstance() } returns mockLspSettings

        mockkStatic(ExecUtil::class)
        mockkStatic(::getStartUrl)
        every { getStartUrl(any()) } returns "https://start.url"
        every { ExecUtil.execAndGetOutput(any(), any<Int>()) } returns ProcessOutput("v99.0.0", "", 0, false, false)

        val result = NodeExePatcher.getNodeRuntimePath(mockk(), Paths.get(pathToNode))

        // Should use the user-provided executable path
        assertThat(result).isEqualTo(nodeExe.toAbsolutePath())
    }
}
