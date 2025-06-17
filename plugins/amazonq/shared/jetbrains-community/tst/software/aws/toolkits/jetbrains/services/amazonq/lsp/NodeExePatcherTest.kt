// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.system.CpuArch
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.rules.EnvironmentVariableHelper
import software.aws.toolkits.core.utils.exists
import java.nio.file.Paths

class NodeExePatcherTest {
    @get:Rule
    val envVarHelper = EnvironmentVariableHelper()

    @get:Rule
    val tempDir = TempDirectory()

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
}
