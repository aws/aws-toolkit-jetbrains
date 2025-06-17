// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.system.CpuArch
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Hacky nonsense to support old glibc platforms like AL2
 * @see "https://github.com/microsoft/vscode/issues/231623"
 * @see "https://github.com/aws/aws-toolkit-vscode/commit/6081f890bdbb91fcd8b60c4cc0abb65b15d4a38d"
 */
object NodeExePatcher {
    const val GLIBC_LINKER_VAR = "VSCODE_SERVER_CUSTOM_GLIBC_LINKER"
    const val GLIBC_PATH_VAR = "VSCODE_SERVER_CUSTOM_GLIBC_PATH"
    const val INTERNAL_AARCH64_LINKER = "/opt/vsc-sysroot/lib/ld-linux-aarch64.so.1"
    const val INTERNAL_X86_64_LINKER = "/opt/vsc-sysroot/lib/ld-linux-x86-64.so.2"
    const val INTERNAL_GLIBC_PATH = "/opt/vsc-sysroot/lib/"

    fun patch(node: Path): GeneralCommandLine {
        val nodePath = node.toAbsolutePath().toString()

        return if (Paths.get(linker).exists() && Paths.get(glibc).exists()) {
            GeneralCommandLine(linker)
                .withParameters("--library-path", glibc, nodePath)
                .also {
                    getLogger<NodeExePatcher>().info { "Using glibc patch: $it" }
                }
        } else {
            GeneralCommandLine(nodePath)
        }
    }

    private val linker
        get() = System.getenv(GLIBC_LINKER_VAR) ?: let {
            if (CpuArch.isArm64()) {
                INTERNAL_AARCH64_LINKER
            } else {
                INTERNAL_X86_64_LINKER
            }
        }

    private val glibc
        get() = System.getenv(GLIBC_PATH_VAR) ?: INTERNAL_GLIBC_PATH
}
