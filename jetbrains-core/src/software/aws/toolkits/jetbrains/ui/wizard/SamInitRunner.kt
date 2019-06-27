// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.resources.message
import java.io.FileFilter

object SamInitRunner {
    fun execute(
        name: String,
        outputDir: VirtualFile,
        runtime: Runtime,
        location: String? = null,
        dependencyManager: String? = null
    ) = ApplicationManager.getApplication().runWriteAction {
        // set output to a temp dir
        val tempDir = createTempDir()
        val commandLine = SamCommon.getSamCommandLine()
            .withParameters("init")
            .withParameters("--no-input")
            .withParameters("--name")
            .withParameters(name)
            .withParameters("--runtime")
            .withParameters(runtime.toString())
            .withParameters("--output-dir")
            .withParameters(tempDir.path)
            .apply {
                location?.let {
                    this.withParameters("--location")
                        .withParameters(it)
                }

                dependencyManager?.let {
                    this.withParameters("--dependency-manager")
                        .withParameters(it)
                }
            }

        val process = CapturingProcessHandler(commandLine).runProcess()
        if (process.exitCode != 0) {
            throw RuntimeException("${message("sam.init.execution_error")}: ${process.stderrLines.last()}")
        }

        val rootFolder = tempDir.listFiles(FileFilter { it.isDirectory })?.firstOrNull()
            ?: throw RuntimeException(message("sam.init.error.no.root_folder"))

        FileUtil.copyDirContent(rootFolder, VfsUtil.virtualToIoFile(outputDir))
        FileUtil.delete(tempDir)
    }
}