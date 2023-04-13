// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig

import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootType
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.cannotFindBuildArtifacts
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.fileTooLarge
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.JAVA_CODE_SCAN_TIMEOUT_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.JAVA_PAYLOAD_LIMIT_IN_BYTES
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.IOException
import java.lang.IndexOutOfBoundsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal class JavaCodeScanSessionConfig(
    private val selectedFile: VirtualFile,
    private val project: Project
) : CodeScanSessionConfig(selectedFile, project) {

    private val packageRegex = Regex("package\\s+([\\w.]+)\\s*;")
    private val importRegex = Regex("import\\s+([\\w.]+[*]?)\\s*;")
    private val buildExt = ".class"
    override val sourceExt = ".java"

    data class JavaImportsInfo(val imports: List<String>, val packagePath: String)

    override fun overallJobTimeoutInSeconds(): Long = JAVA_CODE_SCAN_TIMEOUT_IN_SECONDS

    override fun getPayloadLimitInBytes(): Int = JAVA_PAYLOAD_LIMIT_IN_BYTES

    override fun createPayload(): Payload {
        // Fail fast if the selected file size is greater than the payload limit.
        if (selectedFile.length > getPayloadLimitInBytes()) {
            fileTooLarge(getPresentablePayloadLimit())
        }

        val start = Instant.now().toEpochMilli()

        LOG.debug { "Creating payload. File selected as root for the context truncation: ${selectedFile.path}" }

        // Include all the dependencies using BFS
        val (sourceFiles, srcPayloadSize, totalLines, buildPaths) = includeDependencies()

        var noClassFilesFound = true
        val outputPaths = CompilerPaths.getOutputPaths(ModuleManager.getInstance(project).modules)
        var totalBuildPayloadSize = 0L
        val buildFiles = buildPaths.mapNotNull { relativePath ->
            val classFile = findClassFile(relativePath, outputPaths)
            if (classFile == null) {
                LOG.debug { "Cannot find class file for $relativePath" }
            } else {
                noClassFilesFound = false
                totalBuildPayloadSize += classFile.toFile().length()
            }
            classFile
        }
        LOG.debug { "Total build files sent in payload: ${buildFiles.size}" }
        if (noClassFilesFound) cannotFindBuildArtifacts()

        // Copy all the included source and build files to the source zip
        val srcZip = zipFiles(sourceFiles.mapNotNull { getPath(it) } + buildFiles)

        val payloadContext = PayloadContext(
            CodewhispererLanguage.Java,
            totalLines,
            sourceFiles.size,
            Instant.now().toEpochMilli() - start,
            sourceFiles.mapNotNull { Path.of(it).toFile().toVirtualFile() },
            srcPayloadSize,
            srcZip.length(),
            totalBuildPayloadSize
        )
        return Payload(payloadContext, srcZip)
    }

    private fun findClassFile(relativePath: String, outputPaths: Array<String>): Path? {
        outputPaths.forEach { outputPath ->
            val classFile = getPath(outputPath, relativePath)
            if (classFile?.exists() == true) return classFile
        }
        return null
    }

    fun parseImports(file: VirtualFile): JavaImportsInfo {
        val imports = mutableSetOf<String>()
        val inputStream = file.inputStream
        var packagePath = ""
        try {
            inputStream.use {
                it.bufferedReader().lines().forEach { line ->
                    val importMatcher = importRegex.toPattern().matcher(line)
                    val packageMatcher = packageRegex.toPattern().matcher(line)
                    if (importMatcher.find()) {
                        val import = importMatcher.group(1).replace('.', FILE_SEPARATOR)
                        imports.add(import)
                    }
                    if (packageMatcher.find()) {
                        packagePath = packageMatcher.group(1).replace('.', FILE_SEPARATOR)
                    }
                }
            }
        } catch (e: IOException) {
            LOG.error { message("codewhisperer.codescan.cannot_read_file", file.path) }
        }
        return JavaImportsInfo(imports.toList(), packagePath)
    }

    /**
     * Gets a relative build path for file and package
     */
    private fun getRelativeBuildPath(file: VirtualFile, packagePath: String): String? {
        val sourceFilePath = file.path
        return if (packagePath.isEmpty()) {
            file.nameWithoutExtension
        } else {
            val start = sourceFilePath.lastIndexOf(packagePath)
            val end = sourceFilePath.lastIndexOf('.')
            try {
                sourceFilePath.substring(start, end)
            } catch (e: IndexOutOfBoundsException) {
                return null
            }
        } + buildExt
    }

    override fun getSourceFilesUnderProjectRoot(selectedFile: VirtualFile): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val sourceRoots = ProjectRootManager.getInstance(project).getModuleSourceRoots(setOf(JavaSourceRootType.SOURCE))
        files.add(selectedFile)
        sourceRoots.forEach { vFile ->
            files.addAll(
                VfsUtil.collectChildrenRecursively(vFile).filter {
                    it.path.endsWith(sourceExt) && it != selectedFile
                }
            )
        }
        return files
    }

    override fun includeDependencies(): PayloadMetadata {
        val sourceFiles = mutableSetOf<VirtualFile>()
        val buildPaths = mutableSetOf<String?>()
        var currentTotalFileSize = 0L
        var currentTotalLines = 0L
        val files = getSourceFilesUnderProjectRoot(selectedFile)
        val queue = ArrayDeque<VirtualFile>()

        files.forEach { file ->
            queue.add(file)

            // BFS
            while (queue.isNotEmpty()) {
                if (currentTotalFileSize.equals(getPayloadLimitInBytes())) {
                    return PayloadMetadata(sourceFiles.map { it.path }.toSet(), currentTotalFileSize, currentTotalLines, buildPaths.filterNotNull().toSet())
                }

                val currentFile = queue.removeFirst()
                if (!currentFile.path.startsWith(projectRoot.path) ||
                    sourceFiles.contains(currentFile) ||
                    willExceedPayloadLimit(currentTotalFileSize, currentFile.length)
                ) {
                    if (!currentFile.path.startsWith(projectRoot.path)) {
                        LOG.error { "Invalid workspace: Current file ${currentFile.path} is not under the project root ${projectRoot.path}" }
                    }
                    continue
                }

                val currentFileSize = currentFile.length

                currentTotalFileSize += currentFileSize
                currentTotalLines += Files.lines(currentFile.toNioPath()).count()
                sourceFiles.add(currentFile)

                // Get all imports from the file
                val importsInfo = parseImports(currentFile)
                importsInfo.imports.forEach { importPath ->
                    val importedFiles = getSourceFilesForImport(currentFile, importPath)
                    importedFiles.forEach { importedFile ->
                        if (!sourceFiles.contains(importedFile)) queue.addLast(importedFile)
                    }
                }
                buildPaths.add(getRelativeBuildPath(currentFile, importsInfo.packagePath))
            }
        }

        return PayloadMetadata(sourceFiles.map { it.path }.toSet(), currentTotalFileSize, currentTotalLines, buildPaths.filterNotNull().toSet())
    }

    private fun getImportedFile(currentFile: VirtualFile, importPath: String): VirtualFile? {
        // Handle '*' imports
        val resolvedImportPath = if (importPath.contains('*')) {
            importPath.substring(0, importPath.indexOfFirst { it == '*' } - 1)
        } else {
            importPath + sourceExt
        }

        // First try searching the module containing the current file
        ModuleUtil.findModuleForFile(currentFile, project)?.rootManager?.getSourceRoots(JavaSourceRootType.SOURCE)?.forEach { srcRoot ->
            val path = getPath(srcRoot.path, resolvedImportPath)
            path?.toFile()?.toVirtualFile()?.let {
                return it
            }
        }

        // Fallback to all other java source roots.
        val projectSourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
        projectSourceRoots.forEach { srcRoot ->
            val path = getPath(srcRoot.path, resolvedImportPath)
            path?.toFile()?.toVirtualFile()?.let {
                return it
            }
        }
        return null
    }

    /**
     * Get source files for import statement. If the import is a star import, include all the files in the package directory.
     */
    private fun getSourceFilesForImport(currentFile: VirtualFile, importPath: String): List<VirtualFile> {
        val importedFile = getImportedFile(currentFile, importPath) ?: return listOf()
        if (!importedFile.isDirectory) {
            return listOf(importedFile)
        }
        return VfsUtil.collectChildrenRecursively(importedFile).filter { it.name.endsWith(sourceExt) }
    }

    companion object {
        private val LOG = getLogger<JavaCodeScanSessionConfig>()
    }
}
