// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.services.amazonq.QConstants.MAX_FILE_SIZE_BYTES
import software.aws.toolkits.jetbrains.services.telemetry.ALLOWED_CODE_EXTENSIONS
import software.aws.toolkits.jetbrains.utils.isDevFile
import software.aws.toolkits.resources.AwsCoreBundle
import software.aws.toolkits.telemetry.AmazonqTelemetry
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.relativeTo

interface RepoSizeError {
    val message: String
}
class RepoSizeLimitError(override val message: String) : RuntimeException(), RepoSizeError

open class FeatureDevSessionContext(val project: Project, val maxProjectSizeBytes: Long? = null) {
    // TODO: Need to correct this class location in the modules going further to support both amazonq and codescan.

    private val additionalGitIgnoreFolderRules = setOf(
        ".aws-sam",
        ".gem",
        ".git",
        ".gradle",
        ".hg",
        ".idea",
        ".project",
        ".rvm",
        ".svn",
        "node_modules",
        "build",
        "dist",
    )

    private val defaultAdditionalGitIgnoreBinaryFilesRules = setOf(
        "*.zip",
        "*.bin",
        "*.png",
        "*.jpg",
        "*.svg",
        "*.pyc",
        "license.txt",
        "License.txt",
        "LICENSE.txt",
        "license.md",
        "License.md",
        "LICENSE.md",
    )

    // well known source files that do not have extensions
    private val wellKnownSourceFiles = setOf(
        "Dockerfile",
        "Dockerfile.build",
        "gradlew",
        "mvnw"
    )

    // projectRoot: is the directory where the project is located when selected to open a project.
    val projectRoot = project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")
    private val projectRootPath = Paths.get(projectRoot.path) ?: error("Can not find project root path")

    // selectedSourceFolder: is the directory selected in replacement of the root, this happens when the project is too big to bundle for uploading.
    private var _selectedSourceFolder = projectRoot
    private var ignorePatternsWithGitIgnore = emptyList<Regex>()
    private var ignorePatternsForBinaryFiles = buildIgnorePatternsForBinaryFiles()

    private val gitIgnoreFile = File(selectedSourceFolder.path, ".gitignore")

    init {
        ignorePatternsWithGitIgnore = try {
            buildList {
                addAll(
                    additionalGitIgnoreFolderRules
                        .map { convertGitIgnorePatternToRegex(it) }
                )
                addAll(parseGitIgnore())
            }.mapNotNull { pattern ->
                runCatching { Regex(pattern) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildIgnorePatternsForBinaryFiles(): List<Regex> =
        getAdditionalGitIgnoreBinaryFilesRules()
            .map { convertGitIgnorePatternToRegex(it) }
            .mapNotNull { pattern ->
                runCatching { Regex(pattern) }.getOrNull()
            }

    open fun getAdditionalGitIgnoreBinaryFilesRules(): Set<String> = defaultAdditionalGitIgnoreBinaryFilesRules

    // This function checks for existence of `devfile.yaml` in customer's repository, currently only `devfile.yaml` is supported for this feature.
    fun checkForDevFile(): Boolean {
        val devFile = File(projectRoot.path, "/devfile.yaml")
        return devFile.exists()
    }

    fun getWorkspaceRoot(): String = projectRoot.path

    fun getProjectZip(isAutoBuildFeatureEnabled: Boolean?): ZipCreationResult {
        val zippedProject = runBlocking {
            withBackgroundProgress(project, AwsCoreBundle.message("amazonqFeatureDev.placeholder.generating_code")) {
                zipFiles(selectedSourceFolder, isAutoBuildFeatureEnabled)
            }
        }
        val checkSum256: String = Base64.getEncoder().encodeToString(DigestUtils.sha256(FileInputStream(zippedProject)))
        return ZipCreationResult(zippedProject, checkSum256, zippedProject.length())
    }

    open fun isFileExtensionAllowed(file: VirtualFile): Boolean {
        // if it is a directory, it is allowed
        if (file.isDirectory) return true
        val extension = file.extension ?: return false
        return ALLOWED_CODE_EXTENSIONS.contains(extension)
    }

    fun ignoreFile(file: VirtualFile, applyExtraBinaryFilesRules: Boolean = true): Boolean = ignoreFile(file.presentableUrl, applyExtraBinaryFilesRules)

    fun ignoreFile(path: String, applyExtraBinaryFilesRules: Boolean = true): Boolean {
        val allIgnoreRules = if (applyExtraBinaryFilesRules) ignorePatternsWithGitIgnore + ignorePatternsForBinaryFiles else ignorePatternsWithGitIgnore
        val matchedRules = allIgnoreRules.map { pattern ->
            // avoid partial match (pattern.containsMatchIn) since it causes us matching files
            // against folder patterns. (e.g. settings.gradle ignored by .gradle rule!)
            // we convert the glob rules to regex, add a trailing /* to all rules and then match
            // entries against them by adding a trailing /.
            // TODO: Add unit tests for gitignore matching
            val relative = if (path.startsWith(projectRootPath.toString())) Paths.get(path).relativeTo(projectRootPath) else path
            pattern.matches("$relative/")
        }
        return matchedRules.any { it }
    }

    private fun wellKnown(file: VirtualFile): Boolean = wellKnownSourceFiles.contains(file.name)

    private fun shouldIncludeInZipFile(file: VirtualFile, isAutoBuildFeatureEnabled: Boolean): Boolean {
        // large files always ignored
        if (file.length > MAX_FILE_SIZE_BYTES) {
            return false
        }

        // always respect gitignore rules and remove binary files if auto build is disabled
        val isFileIgnoredByPattern = ignoreFile(file, !isAutoBuildFeatureEnabled)
        if (isFileIgnoredByPattern) {
            return false
        }

        // all other files are included when auto build enabled
        if (isAutoBuildFeatureEnabled) {
            return true
        }

        // when auto build is disabled, only include files with well known extensions and names except "devfile.yam"
        if (!isDevFile(file) && (wellKnown(file) || isFileExtensionAllowed(file))) {
            return true
        }

        // Any other files should not be included
        return false
    }

    suspend fun zipFiles(projectRoot: VirtualFile, isAutoBuildFeatureEnabled: Boolean?): File = withContext(getCoroutineBgContext()) {
        val files = mutableListOf<VirtualFile>()
        val ignoredExtensionMap = mutableMapOf<String, Long>().withDefault { 0L }
        var totalSize: Long = 0

        VfsUtil.visitChildrenRecursively(
            projectRoot,
            object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    val isIncluded = shouldIncludeInZipFile(file, isAutoBuildFeatureEnabled == true)
                    if (!isIncluded) {
                        val extension = file.extension.orEmpty()
                        ignoredExtensionMap[extension] = (ignoredExtensionMap[extension] ?: 0) + 1
                        return false
                    }

                    if (file.isFile) {
                        totalSize += file.length
                        files.add(file)

                        if (maxProjectSizeBytes != null && totalSize > maxProjectSizeBytes) {
                            throw RepoSizeLimitError(AwsCoreBundle.message("amazonqFeatureDev.content_length.error_text"))
                        }
                    }
                    return true
                }
            }
        )

        for ((key, value) in ignoredExtensionMap) {
            AmazonqTelemetry.bundleExtensionIgnored(
                count = value,
                filenameExt = key
            )
        }

        // Process files in parallel
        val filesToIncludeFlow = channelFlow {
            // chunk with some reasonable number because we don't actually need a new job for each file
            files.chunked(50).forEach { chunk ->
                launch {
                    for (file in chunk) {
                        send(file)
                    }
                }
            }
        }

        val zipFilePath = createTemporaryZipFileAsync { zipfs ->
            val posixFileAttributeSubstr = "posix"
            val isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains(posixFileAttributeSubstr)
            filesToIncludeFlow.collect { file ->

                if (!file.isDirectory) {
                    val externalFilePath = Path(file.path)
                    val relativePath = Path(file.path).relativeTo(projectRootPath)
                    val zipfsPath = zipfs.getPath("/$relativePath")
                    withContext(getCoroutineBgContext()) {
                        zipfsPath.createParentDirectories()
                        try {
                            Files.copy(externalFilePath, zipfsPath, StandardCopyOption.REPLACE_EXISTING)
                            if (isPosix) {
                                val zipPermissionAttributeName = "zip:permissions"
                                Files.setAttribute(zipfsPath, zipPermissionAttributeName, externalFilePath.getPosixFilePermissions())
                            }
                        } catch (e: NoSuchFileException) {
                            // Noop: Skip if file was deleted
                        }
                    }
                }
            }
        }
        zipFilePath
    }.toFile()

    private suspend fun createTemporaryZipFileAsync(block: suspend (FileSystem) -> Unit): Path = withContext(getCoroutineBgContext()) {
        // Don't use Files.createTempFile since the file must not be created for ZipFS to work
        val tempFilePath: Path = Paths.get(FileUtils.getTempDirectory().absolutePath, "${UUID.randomUUID()}.zip")
        val uri = URI.create("jar:${tempFilePath.toUri()}")
        val env = hashMapOf("create" to "true")
        val zipfs = FileSystems.newFileSystem(uri, env)
        zipfs.use {
            block(zipfs)
        }
        tempFilePath
    }

    private fun parseGitIgnore(): Set<String> {
        if (!gitIgnoreFile.exists()) {
            return emptySet()
        }
        return gitIgnoreFile.readLines()
            .filterNot { it.isBlank() || it.startsWith("#") }
            .map { it.trim() }
            .map { convertGitIgnorePatternToRegex(it) }
            .toSet()
    }

    // gitignore patterns are not regex, method update needed.
    fun convertGitIgnorePatternToRegex(pattern: String): String {
        // Special case for ".*" to match only dotfiles
        if (pattern == ".*") {
            return "^\\..*/.*"
        }

        return pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .let { if (it.endsWith("/")) "$it.*" else "$it/.*" } // Add a trailing /* to all patterns. (we add a trailing / to all files when matching)
    }
    var selectedSourceFolder: VirtualFile
        set(newRoot) {
            _selectedSourceFolder = newRoot
        }
        get() = _selectedSourceFolder
}

data class ZipCreationResult(val payload: File, val checksum: String, val contentLength: Long)
