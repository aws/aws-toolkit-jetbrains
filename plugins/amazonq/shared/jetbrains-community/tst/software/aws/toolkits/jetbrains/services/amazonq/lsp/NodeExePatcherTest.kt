// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.rules.EnvironmentVariableHelper
import kotlin.io.path.Path

class NodeExePatcherTest {
    @get:Rule
    val envVarHelper = EnvironmentVariableHelper()

    private val pathToNode = Path("/path/to/node").toAbsolutePath().toString()

    @Test
    fun `patches if path available`() {
        envVarHelper[NodeExePatcher.GLIBC_LINKER_VAR] = "/opt/vsc-sysroot/lib/ld-linux-x86-64.so.2"
        envVarHelper[NodeExePatcher.GLIBC_PATH_VAR] = "/opt/vsc-sysroot/lib/"

        assertThat(NodeExePatcher.patch(Path("/path/to/node")))
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine("/opt/vsc-sysroot/lib/ld-linux-x86-64.so.2", "--library-path", "/opt/vsc-sysroot/lib/", pathToNode))
    }

    @Test
    fun `noop if no patch available`() {
        assertThat(NodeExePatcher.patch(Path("/path/to/node")))
            .usingComparator(Comparator.comparing { it.commandLineString })
            .isEqualTo(GeneralCommandLine(pathToNode))
    }
}
