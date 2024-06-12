// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.cannotFindFile
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.fileTooLarge
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.noFileOpenError
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.noSupportedFilesError
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CODE_SCAN_CREATE_PAYLOAD_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CodeAnalysisScope
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.DEFAULT_CODE_SCAN_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.DEFAULT_PAYLOAD_LIMIT_IN_BYTES
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.FILE_SCAN_PAYLOAD_SIZE_LIMIT_IN_BYTES
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.FILE_SCAN_TIMEOUT_IN_SECONDS
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.Stack
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

class CodeScanSessionConfig(
    private val selectedFile: VirtualFile?,
    private val project: Project,
    private val scope: CodeAnalysisScope
) {
    var projectRoot = project.basePath?.let { Path.of(it) }?.toFile()?.toVirtualFile() ?: run {
        project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")
    }
        private set

    private val featureDevSessionContext = FeatureDevSessionContext(project)

    val fileIndex = ProjectRootManager.getInstance(project).fileIndex

    /**
     * Timeout for the overall job - "Run Security Scan".
     */
    fun overallJobTimeoutInSeconds(): Long = when (scope) {
        CodeAnalysisScope.FILE -> FILE_SCAN_TIMEOUT_IN_SECONDS
        else -> DEFAULT_CODE_SCAN_TIMEOUT_IN_SECONDS
    }

    fun getPayloadLimitInBytes(): Long = when (scope) {
        CodeAnalysisScope.FILE -> (FILE_SCAN_PAYLOAD_SIZE_LIMIT_IN_BYTES)
        else -> (DEFAULT_PAYLOAD_LIMIT_IN_BYTES)
    }

    private fun willExceedPayloadLimit(currentTotalFileSize: Long, currentFileSize: Long): Boolean =
        currentTotalFileSize.let { totalSize -> totalSize > (getPayloadLimitInBytes() - currentFileSize) }

    private var programmingLanguage: CodeWhispererProgrammingLanguage = selectedFile?.programmingLanguage() ?: CodeWhispererUnknownLanguage.INSTANCE

    fun getProgrammingLanguage(): CodeWhispererProgrammingLanguage = programmingLanguage

    fun getSelectedFile(): VirtualFile? = selectedFile

    fun createPayload(): Payload {
        // Fail fast if the selected file is null for File Scan
        if (scope == CodeAnalysisScope.FILE && selectedFile == null) {
            noFileOpenError()
        }

        // Fail fast if the selected file size is greater than the payload limit.
        if (selectedFile != null && selectedFile.length > getPayloadLimitInBytes()) {
            fileTooLarge()
        }

        val start = Instant.now().toEpochMilli()

        LOG.debug { "Creating payload. File selected as root for the context truncation: ${projectRoot.path}" }

        val payloadMetadata = when (selectedFile) {
            null -> getProjectPayloadMetadata()
            else -> when (scope) {
                CodeAnalysisScope.PROJECT -> getProjectPayloadMetadata()
                CodeAnalysisScope.FILE -> if (selectedFile.path.startsWith(projectRoot.path)) {
                    getFilePayloadMetadata(selectedFile)
                } else {
                    projectRoot = selectedFile.parent
                    getFilePayloadMetadata(selectedFile)
                }
            }
        }

        // Copy all the included source files to the source zip
        val srcZip = when (scope) {
            CodeAnalysisScope.PROJECT -> zipProject(payloadMetadata.sourceFiles.map { Path.of(it) })
            CodeAnalysisScope.FILE -> zipFile(Path.of(payloadMetadata.sourceFiles.first()))
        }
        val payloadContext = PayloadContext(
            payloadMetadata.language,
            payloadMetadata.linesScanned,
            payloadMetadata.sourceFiles.size,
            Instant.now().toEpochMilli() - start,
            payloadMetadata.sourceFiles.mapNotNull { Path.of(it).toFile().toVirtualFile() },
            payloadMetadata.payloadSize,
            srcZip.length()
        )

        return Payload(payloadContext, srcZip)
    }

    private fun getFilePayloadMetadata(file: VirtualFile): PayloadMetadata {
        try {
            return PayloadMetadata(
                setOf(file.path),
                file.length,
                countLinesInVirtualFile(file).toLong(),
                file.programmingLanguage().toTelemetryType()
            )
        } catch (e: Exception) {
            cannotFindFile("File payload creation error: ${e.message}", file.path)
        }
    }

    /**
     * Timeout for creating the payload [createPayload]
     */
    fun createPayloadTimeoutInSeconds(): Long = CODE_SCAN_CREATE_PAYLOAD_TIMEOUT_IN_SECONDS

    private fun countLinesInVirtualFile(virtualFile: VirtualFile): Int {
        try {
            val bufferedReader = virtualFile.inputStream.bufferedReader()
            return bufferedReader.useLines { lines -> lines.count() }
        } catch (e: Exception) {
            cannotFindFile("Line count error: ${e.message}", virtualFile.path)
        }
    }

    private fun zipProject(files: List<Path>): File = createTemporaryZipFile {
        files.forEach { file ->
            try {
                val relativePath = file.relativeTo(projectRoot.toNioPath())
                LOG.debug { "Selected file for truncation: $file" }
                it.putNextEntry(relativePath.toString(), file)
            } catch (e: Exception) {
                cannotFindFile("Zipping error: ${e.message}", file.pathString)
            }
        }
    }.toFile()

    private fun zipFile(filePath: Path): File = createTemporaryZipFile {
        try {
            val relativePath = filePath.relativeTo(projectRoot.toNioPath())
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(filePath)
            virtualFile?.let { file ->
                val document = runReadAction {
                    FileDocumentManager.getInstance().getDocument(file)
                }
                document?.let { doc ->
                    it.putNextEntry(relativePath.toString(), doc.text.encodeToByteArray())
                }
            }
        } catch (e: Exception) {
            cannotFindFile("Zipping error: ${e.message}", filePath.pathString)
        }
    }.toFile()

    fun getProjectPayloadMetadata(): PayloadMetadata {
        val files = mutableSetOf<String>()
        val traversedDirectories = mutableSetOf<VirtualFile>()
        val stack = Stack<VirtualFile>()
        var currentTotalFileSize = 0L
        var currentTotalLines = 0L
        val languageCounts = mutableMapOf<CodeWhispererProgrammingLanguage, Int>()

        moduleLoop@ for (module in project.modules) {
            val changeListManager = ChangeListManager.getInstance(module.project)
            if (module.guessModuleDir() != null) {
                stack.push(module.guessModuleDir())
                while (stack.isNotEmpty()) {
                    val current = stack.pop()

                    if (!current.isDirectory) {
                        if (current.isFile && !changeListManager.isIgnoredFile(current) &&
                            runBlocking { !featureDevSessionContext.ignoreFile(current, this) } &&
                            runReadAction { !fileIndex.isInLibrarySource(current) }
                        ) {
                            if (willExceedPayloadLimit(currentTotalFileSize, current.length)) {
                                fileTooLarge()
                            } else {
                                try {
                                    val language = current.programmingLanguage()
                                    if (language !is CodeWhispererUnknownLanguage) {
                                        languageCounts[language] = (languageCounts[language] ?: 0) + 1
                                    }
                                    files.add(current.path)
                                    currentTotalFileSize += current.length
                                    currentTotalLines += countLinesInVirtualFile(current)
                                } catch (e: Exception) {
                                    LOG.debug { "Error parsing the file: ${current.path} with error: ${e.message}" }
                                    continue
                                }
                            }
                        }
                    } else {
                        // Directory case: only traverse if not ignored
                        if (!changeListManager.isIgnoredFile(current) &&
                            runBlocking { !featureDevSessionContext.ignoreFile(current, this) } &&
                            !traversedDirectories.contains(current) && current.isValid &&
                            runReadAction { !fileIndex.isInLibrarySource(current) }
                        ) {
                            for (child in current.children) {
                                stack.push(child)
                            }
                        }
                        traversedDirectories.add(current)
                    }
                }
            }
        }

        val maxCount = languageCounts.maxByOrNull { it.value }?.value ?: 0
        val maxCountLanguage = languageCounts.filter { it.value == maxCount }.keys.firstOrNull()

        if (maxCountLanguage == null) {
            programmingLanguage = CodeWhispererUnknownLanguage.INSTANCE
            noSupportedFilesError()
        }
        programmingLanguage = maxCountLanguage
        return PayloadMetadata(files, currentTotalFileSize, currentTotalLines, maxCountLanguage.toTelemetryType())
    }

    fun getPath(root: String, relativePath: String = ""): Path? = try {
        Path.of(root, relativePath).normalize()
    } catch (e: Exception) {
        LOG.debug { "Cannot find file at path $relativePath relative to the root $root" }
        null
    }

    fun File.toVirtualFile() = LocalFileSystem.getInstance().findFileByIoFile(this)

    companion object {
        private val LOG = getLogger<CodeScanSessionConfig>()
        fun create(file: VirtualFile?, project: Project, scope: CodeAnalysisScope): CodeScanSessionConfig = CodeScanSessionConfig(file, project, scope)
    }
}

data class Payload(
    val context: PayloadContext,
    val srcZip: File
)

data class PayloadContext(
    val language: CodewhispererLanguage,
    val totalLines: Long,
    val totalFiles: Int,
    val totalTimeInMilliseconds: Long,
    val scannedFiles: List<VirtualFile>,
    val srcPayloadSize: Long,
    val srcZipFileSize: Long,
    val buildPayloadSize: Long? = null
)

data class PayloadMetadata(
    val sourceFiles: Set<String>,
    val payloadSize: Long,
    val linesScanned: Long,
    val language: CodewhispererLanguage
)
