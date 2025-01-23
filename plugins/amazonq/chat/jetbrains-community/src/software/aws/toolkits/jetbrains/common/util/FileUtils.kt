// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.common.util

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

fun resolveAndCreateOrUpdateFile(projectRootPath: Path, relativeFilePath: String, fileContent: String) {
    val filePath = projectRootPath.resolve(relativeFilePath)
    filePath.parent.createDirectories() // Create directories if needed
    filePath.writeBytes(fileContent.toByteArray(Charsets.UTF_8))
}

fun resolveAndDeleteFile(projectRootPath: Path, relativePath: String) {
    val filePath = projectRootPath.resolve(relativePath)
    filePath.deleteIfExists()
}

fun selectFolder(project: Project, openOn: VirtualFile): VirtualFile? {
    val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    return FileChooser.chooseFile(fileChooserDescriptor, project, openOn)
}

fun readFileToString(file: File): String {
    val charsetToolkit = CharsetToolkit(file.readBytes(), Charset.forName("UTF-8"), false)
    val charset = charsetToolkit.guessEncoding(4096)
    return file.readText(charset)
}

/**
 * Calculates the number of added characters and lines between existing content and LLM response
 *
 * @param existingContent The original text content before changes
 * @param llmResponse The new text content from the LLM
 * @return A Map containing:
 *         - "addedChars": Total number of new characters added
 *         - "addedLines": Total number of new lines added
 */
data class DiffResult(val addedChars: Int, val addedLines: Int)

fun getDiffCharsAndLines(
    existingContent: String,
    llmResponse: String,
): DiffResult {
    var addedChars = 0
    var addedLines = 0

    val existingLines = existingContent.lines()
    val llmLines = llmResponse.lines()

    val patch = DiffUtils.diff(existingLines, llmLines)

    for (delta in patch.deltas) {
        when (delta.type) {
            DeltaType.INSERT -> {
                addedChars += delta.target.lines.sumOf { it.length }
                addedLines += delta.target.lines.size
            }

            DeltaType.CHANGE -> {
                addedChars += delta.target.lines.sumOf { it.length }
                addedLines += delta.target.lines.size
            }

            else -> {} // Do nothing for DELETE
        }
    }

    return DiffResult(addedChars, addedLines)
}
