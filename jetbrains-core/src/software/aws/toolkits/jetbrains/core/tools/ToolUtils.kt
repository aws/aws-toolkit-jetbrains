// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.Decompressor
import software.aws.toolkits.jetbrains.services.ssm.SamCli
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.streams.asSequence

private val EXECUTION_TIMEOUT = Duration.ofSeconds(5)

fun <VersionScheme : Version> ManagedToolType<VersionScheme>.findExe(executableName: String, installDir: Path): Tool<ToolType<VersionScheme>> =
    Files.walk(installDir).use { files ->
        files.asSequence().filter { it.fileName.toString() == executableName && Files.isExecutable(it) }
            .map { Tool(this, it) }
            .firstOrNull()
    } ?: throw IllegalStateException("Failed to locate $executableName under $installDir")

fun extractZip(downloadArtifact: Path, destinationDir: Path) {
    val decompressor = Decompressor.Zip(downloadArtifact).withZipExtensions()
    if (!SystemInfo.isWindows) {
        decompressor.extract(destinationDir)
        return
    }

    // on windows there is a zip inside a zip :(
    val tempDir = Files.createTempDirectory(SamCli.id)
    decompressor.extract(tempDir)

    val intermediateZip = tempDir.resolve("package.zip")
    Decompressor.Zip(intermediateZip).withZipExtensions().extract(destinationDir)
}

fun hasCommand(cmd: String): Boolean {
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine("sh", "-c", "command -v $cmd"), EXECUTION_TIMEOUT.toMillis().toInt())
    return output.exitCode == 0
}
