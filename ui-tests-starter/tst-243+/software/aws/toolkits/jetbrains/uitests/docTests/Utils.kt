// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests

import java.io.File
import java.nio.file.Paths

fun prepTestData(isCreate: Boolean) {
    val process: Process
    if (isCreate) {
        val path = Paths.get("tstData", "qdoc", "createFlow", "README.md").toUri()
        if (File(path).exists()) {
            File(path).delete()
        }
        // check multi-root workspace readme as well
        val path2 = Paths.get("tstData", "qdoc", "README.md").toUri()
        if (File(path2).exists()) {
            File(path2).delete()
        }
    } else {
        val path = Paths.get("tstData", "qdoc", "updateFlow", "README.md").toUri()
        process = ProcessBuilder("git", "restore", path.path).start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("Warning: git stash command failed with exit code $exitCode")
            process.errorStream.bufferedReader().use { reader ->
                println("Error: ${reader.readText()}")
            }
        }
    }
}
