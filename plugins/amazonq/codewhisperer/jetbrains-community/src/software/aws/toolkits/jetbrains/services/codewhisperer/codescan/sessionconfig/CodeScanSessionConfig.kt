// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import kotlinx.coroutines.runBlocking
import software.amazon.q.core.utils.createTemporaryZipFile
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.cannotFindBuildArtifacts
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.cannotFindFile
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.fileTooLarge
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.noFileOpenError
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.noSupportedFilesError
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.AmazonQCodeReviewGitUtils.getUnstagedFiles
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.AmazonQCodeReviewGitUtils.isGitRoot
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.AmazonQCodeReviewGitUtils.runGitDiffHead
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CODE_SCAN_CREATE_PAYLOAD_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CodeAnalysisScope
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.DEFAULT_CODE_SCAN_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.DEFAULT_PAYLOAD_LIMIT_IN_BYTES
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.EXPRESS_SCAN_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.FILE_SCAN_PAYLOAD_SIZE_LIMIT_IN_BYTES
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getNormalizedRelativePath
import software.aws.toolkits.jetbrains.services.codewhisperer.util.GitIgnoreFilteringUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.util.isWithin
import software.aws.toolkits.resources.message
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
    private val scope: CodeAnalysisScope,
    private val initiatedByChat: Boolean,
) {
    var projectRoot = project.basePath?.let { Path.of(it) }?.toFile()?.toVirtualFile() ?: run {
        project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")
    }
        private set

    val fileIndex = ProjectRootManager.getInstance(project).fileIndex

    fun isInitiatedByChat(): Boolean = initiatedByChat

    /**
     * Timeout for the overall job - "Run Security Scan".
     */
    fun overallJobTimeoutInSeconds(): Long {
        if (scope == CodeAnalysisScope.FILE && !initiatedByChat) {
            return EXPRESS_SCAN_TIMEOUT_IN_SECONDS
        }
        return DEFAULT_CODE_SCAN_TIMEOUT_IN_SECONDS
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

        val payloadMetadata: PayloadMetadata = try {
            when (selectedFile) {
                null -> getProjectPayloadMetadata()
                else -> when (scope) {
                    CodeAnalysisScope.PROJECT -> getProjectPayloadMetadata()
                    CodeAnalysisScope.AGENTIC -> getProjectPayloadMetadata()
                    CodeAnalysisScope.FILE -> if (selectedFile.isWithin(projectRoot)) {
                        getFilePayloadMetadata(selectedFile, true)
                    } else {
                        projectRoot = selectedFile.parent
                        getFilePayloadMetadata(selectedFile)
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Illegal repetition near index") == true -> "Illegal repetition near index"
                else -> e.message
            }
            LOG.debug { "Error creating payload metadata: $errorMessage" }
            throw cannotFindBuildArtifacts(errorMessage ?: message("codewhisperer.codescan.run_scan_error_telemetry"))
        }

        // Copy all the included source files to the source zip
        val srcZip = zipFiles(payloadMetadata.sourceFiles.map { Path.of(it) }, payloadMetadata.codeDiff)
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

    private fun getFilePayloadMetadata(file: VirtualFile, getCodeDiff: Boolean? = false): PayloadMetadata {
        try {
            val gitDiffContent = if (initiatedByChat && getCodeDiff == true) {
                getFileGitDiffContent(file)
            } else {
                null
            }
            return PayloadMetadata(
                setOf(file.path),
                file.length,
                countLinesInVirtualFile(file).toLong(),
                file.programmingLanguage().toTelemetryType(),
                gitDiffContent
            )
        } catch (e: Exception) {
            cannotFindFile("File payload creation error: ${e.message}", file.path)
        }
    }

    private fun getFileGitDiffContent(file: VirtualFile): String {
        if (!file.exists()) {
            LOG.debug { "File does not exist: ${file.path}" }
            return ""
        }
        try {
            val projectRootNio = projectRoot.toNioPath()
            val fileNio = file.toNioPath()

            return buildString {
                append("+++ b/")
                append(project.name)
                append('/')
                append(fileNio.relativeTo(projectRootNio).toString().replace(File.separator, "/"))
            }
        } catch (e: Exception) {
            LOG.debug(e) { "Failed to create git diff" }
            return ""
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

    private fun zipFiles(files: List<Path>, codeDiff: String? = null): File = createTemporaryZipFile {
        files.forEach { file ->
            try {
                val relativePath = getNormalizedRelativePath(project.name, file.relativeTo(projectRoot.toNioPath()))
                LOG.debug { "Selected file for truncation: $file" }
                it.putNextEntry(relativePath, file)
            } catch (e: Exception) {
                cannotFindFile("Zipping error: ${e.message}", file.pathString)
            }
        }

        codeDiff?.takeIf { diff ->
            initiatedByChat && diff.isNotEmpty()
        }?.let { diff ->
            try {
                LOG.debug { "Adding Code.Diff file to zip" }
                diff.byteInputStream(Charsets.UTF_8).buffered().use { inputStream ->
                    it.putNextEntry("codeDiff/code.diff", inputStream)
                }
            } catch (e: Exception) {
                LOG.error(e) { "Failed to add Code.Diff" }
            }
        }
    }.toFile()

    fun getProjectPayloadMetadata(): PayloadMetadata {
        val files = mutableSetOf<String>()
        val traversedDirectories = mutableSetOf<VirtualFile>()
        val stack = Stack<VirtualFile>()
        var currentTotalFileSize = 0L
        var currentTotalLines = 0L
        val languageCounts = mutableMapOf<CodeWhispererProgrammingLanguage, Int>()
        var gitDiffContent = ""

        moduleLoop@ for (module in project.modules) {
            val changeListManager = ChangeListManager.getInstance(module.project)
            module.guessModuleDir()?.let { moduleDir ->
                val gitIgnoreFilteringUtil = GitIgnoreFilteringUtil(moduleDir)
                stack.push(moduleDir)
                while (stack.isNotEmpty()) {
                    val current = stack.pop()

                    if (!current.isDirectory) {
                        if (current.isFile && !changeListManager.isIgnoredFile(current) &&
                            runBlocking { !gitIgnoreFilteringUtil.ignoreFile(current) } &&
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
                        try {
                            if (isGitRoot(current)) {
                                LOG.debug { "$current is git directory" }
                                gitDiffContent = buildString {
                                    append(runGitDiffHead(project.name, current))
                                    getUnstagedFiles(current).takeIf { it.isNotEmpty() }?.let { unstagedFiles ->
                                        unstagedFiles
                                            .asSequence()
                                            .map { relativePath -> runGitDiffHead(project.name, current, relativePath, true) }
                                            .filter { it.isNotEmpty() }
                                            .forEach { diff ->
                                                if (isNotEmpty()) append('\n')
                                                append(diff)
                                            }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            LOG.debug { "Error parsing the git diff for repository $current" }
                        }
                        // Directory case: only traverse if not ignored
                        if (!changeListManager.isIgnoredFile(current) &&
                            runBlocking { !gitIgnoreFilteringUtil.ignoreFile(current) } &&
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
        return PayloadMetadata(files, currentTotalFileSize, currentTotalLines, maxCountLanguage.toTelemetryType(), gitDiffContent)
    }

    fun File.toVirtualFile() = LocalFileSystem.getInstance().findFileByIoFile(this)

    companion object {
        private val LOG = getLogger<CodeScanSessionConfig>()
        fun create(file: VirtualFile?, project: Project, scope: CodeAnalysisScope, initiatedByChat: Boolean): CodeScanSessionConfig = CodeScanSessionConfig(
            file,
            project,
            scope,
            initiatedByChat
        )
    }
}

data class Payload(
    val context: PayloadContext,
    val srcZip: File,
)

data class PayloadContext(
    val language: CodewhispererLanguage,
    val totalLines: Long,
    val totalFiles: Int,
    val totalTimeInMilliseconds: Long,
    val scannedFiles: List<VirtualFile>,
    val srcPayloadSize: Long,
    val srcZipFileSize: Long,
    val payloadManifest: Set<Pair<String, Long>>? = null,
    val payloadLimitCrossed: Boolean? = false,
)

data class PayloadMetadata(
    val sourceFiles: Set<String>,
    val payloadSize: Long,
    val linesScanned: Long,
    val language: CodewhispererLanguage,
    val codeDiff: String? = null,
    val payloadManifest: Set<Pair<String, Long>>? = null,
    val payloadLimitCrossed: Boolean? = false,
)
