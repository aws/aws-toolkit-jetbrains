// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.fileFormatNotSupported
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.fileTooLarge
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.codeWhispererLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CODE_SCAN_CREATE_PAYLOAD_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_BYTES_IN_KB
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_BYTES_IN_MB
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal sealed class CodeScanSessionConfig(
    private val selectedFile: VirtualFile,
    private val project: Project
) {
    protected val projectRoot = project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")

    abstract val sourceExt: String

    /**
     * Timeout for the overall job - "Run Security Scan".
     */
    abstract fun overallJobTimeoutInSeconds(): Long

    abstract fun getPayloadLimitInBytes(): Int

    open fun getImportedFiles(file: VirtualFile, includedSourceFiles: Set<String>): List<String> = listOf()

    open fun createPayload(): Payload {
        // Fail fast if the selected file size is greater than the payload limit.
        if (selectedFile.length > getPayloadLimitInBytes()) {
            fileTooLarge(getPresentablePayloadLimit())
        }

        val start = Instant.now().toEpochMilli()

        LOG.debug { "Creating payload. File selected as root for the context truncation: ${selectedFile.path}" }

        val (includedSourceFiles, payloadSize, totalLines, _) = includeDependencies()

        // Copy all the included source files to the source zip
        val srcZip = zipFiles(includedSourceFiles.map { Path.of(it) })
        val payloadContext = PayloadContext(
            selectedFile.codeWhispererLanguage,
            totalLines,
            includedSourceFiles.size,
            Instant.now().toEpochMilli() - start,
            payloadSize,
            srcZip.length()
        )

        return Payload(payloadContext, srcZip)
    }

    open fun includeDependencies(): PayloadMetadata {
        val includedSourceFiles = mutableSetOf<String>()
        var currentTotalFileSize = 0L
        var currentTotalLines = 0L
        val files = getSourceFilesUnderProjectRoot(selectedFile)
        val queue = ArrayDeque<String>()

        files.forEach { pivotFile ->
            val filePath = pivotFile.path
            queue.addLast(filePath)

            // BFS
            while (queue.isNotEmpty()) {
                if (currentTotalFileSize.equals(getPayloadLimitInBytes())) {
                    return PayloadMetadata(includedSourceFiles, currentTotalFileSize, currentTotalLines)
                }

                val currentFilePath = queue.removeFirst()
                val currentFile = File(currentFilePath).toVirtualFile()
                if (includedSourceFiles.contains(currentFilePath) || currentFile == null) continue

                val currentFileSize = currentFile.length

                // Ignore file if including it exceeds the payload limit.
                if (currentTotalFileSize > getPayloadLimitInBytes() - currentFileSize) continue

                currentTotalFileSize += currentFileSize
                currentTotalLines += Files.lines(currentFile.toNioPath()).count()
                includedSourceFiles.add(currentFilePath)

                getImportedFiles(currentFile, includedSourceFiles).forEach {
                    if (!includedSourceFiles.contains(it)) queue.addLast(it)
                }
            }
        }

        return PayloadMetadata(includedSourceFiles, currentTotalFileSize, currentTotalLines)
    }

    /**
     * Timeout for creating the payload [createPayload]
     */
    open fun createPayloadTimeoutInSeconds(): Long = CODE_SCAN_CREATE_PAYLOAD_TIMEOUT_IN_SECONDS

    open fun getPresentablePayloadLimit(): String = when (getPayloadLimitInBytes() >= TOTAL_BYTES_IN_MB) {
        true -> "${getPayloadLimitInBytes() / TOTAL_BYTES_IN_MB}MB"
        false -> "${getPayloadLimitInBytes() / TOTAL_BYTES_IN_KB}KB"
    }

    protected fun zipFiles(files: List<Path>): File = createTemporaryZipFile {
        files.forEach { file ->
            LOG.debug { "Selected file for truncation: $file" }
            it.putNextEntry(file.toString(), file)
        }
    }.toFile()

    /**
     * Returns all the source files for a given payload type.
     */
    open fun getSourceFilesUnderProjectRoot(selectedFile: VirtualFile): List<VirtualFile> {
        // Include the current selected file
        val files = mutableListOf(selectedFile)
        // Include other files only if the current file is in the project.
        if (selectedFile.path.startsWith(projectRoot.path)) {
            files.addAll(
                VfsUtil.collectChildrenRecursively(projectRoot).filter {
                    it.path.endsWith(sourceExt) && it != selectedFile
                }
            )
        }
        return files
    }

    protected fun getPath(root: String, relativePath: String = ""): Path? = try {
        Path.of(root, relativePath).normalize()
    } catch (e: Exception) {
        LOG.debug { "Cannot find file at path $relativePath relative to the root $root" }
        null
    }

    protected fun File.toVirtualFile() = LocalFileSystem.getInstance().findFileByIoFile(this)

    companion object {
        private val LOG = getLogger<CodeScanSessionConfig>()
        const val FILE_SEPARATOR = '/'
        fun create(file: VirtualFile, project: Project): CodeScanSessionConfig = when (file.codeWhispererLanguage) {
            CodewhispererLanguage.Java -> JavaCodeScanSessionConfig(file, project)
            CodewhispererLanguage.Python -> PythonCodeScanSessionConfig(file, project)
            else -> fileFormatNotSupported(file.extension ?: "")
        }
    }
}

data class Payload(
    val context: PayloadContext,
    val srcZip: File,
    val buildZip: File? = null
)

data class PayloadContext(
    val language: CodewhispererLanguage,
    val totalLines: Long,
    val totalFiles: Int,
    val totalTimeInMilliseconds: Long,
    val srcPayloadSize: Long,
    val srcZipFileSize: Long,
    val buildPayloadSize: Long? = null,
    val buildZipFileSize: Long? = null
)

data class PayloadMetadata(
    val sourceFiles: Set<String>,
    val payloadSize: Long,
    val linesScanned: Long,
    val buildPaths: Set<String> = setOf()
)
