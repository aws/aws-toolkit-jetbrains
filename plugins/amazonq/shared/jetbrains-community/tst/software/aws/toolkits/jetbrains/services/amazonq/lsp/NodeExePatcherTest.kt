// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
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
        // explicitly linux because can't run on mac
        assumeTrue(SystemInfo.isLinux)

        val path = Paths.get(NodeExePatcher.INTERNAL_GLIBC_PATH)
        val linker = Paths.get(NodeExePatcher.INTERNAL_X86_64_LINKER)
        val needsCreate = !path.exists() && !linker.exists()
        if (needsCreate) {
            path.createDirectory()
            linker.createFile()
        }

        try {
            assertThat(NodeExePatcher.patch(Paths.get("/path/to/node")))
                .usingComparator(Comparator.comparing { it.commandLineString })
                .isEqualTo(GeneralCommandLine(linker.toString(), "--library-path", path.toString(), pathToNode))
        } finally {
            if (needsCreate) {
                linker.toFile().delete()
                path.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `noop if no patch available`() {
        assertThat(NodeExePatcher.patch(Paths.get("/path/to/node")))
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(pathToNode))
    }
}
