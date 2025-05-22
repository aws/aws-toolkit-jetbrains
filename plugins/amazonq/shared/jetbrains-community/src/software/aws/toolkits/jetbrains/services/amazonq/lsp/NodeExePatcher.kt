// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import java.nio.file.Path

/**
 * Hacky nonsense to support old glibc platforms like AL2
 * @see https://github.com/microsoft/vscode/issues/231623
 * @see https://github.com/aws/aws-toolkit-vscode/commit/6081f890bdbb91fcd8b60c4cc0abb65b15d4a38d
 */
object NodeExePatcher {
    private const val GLIBC_LINKER_VAR = "VSCODE_SERVER_CUSTOM_GLIBC_LINKER"
    private const val GLIBC_PATH_VAR = "VSCODE_SERVER_CUSTOM_GLIBC_PATH"

    fun patch(node: Path): GeneralCommandLine {
        val linker = System.getenv(GLIBC_LINKER_VAR)
        val glibc = System.getenv(GLIBC_PATH_VAR)
        val nodePath = node.toAbsolutePath().toString()

        if (!linker.isNullOrEmpty() && !glibc.isNullOrEmpty()) {
            return GeneralCommandLine(linker)
                .withParameters("--library-path", glibc, nodePath)
                .also {
                    getLogger<NodeExePatcher>().info { "Using glibc patch: $it" }
                }
        } else {
            return GeneralCommandLine(nodePath)
        }
    }
}
