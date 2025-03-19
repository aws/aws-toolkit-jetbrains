// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
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
import software.aws.toolkits.jetbrains.utils.isWorkspaceDevFile
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

interface RepoSizeError {
    val message: String
}

class RepoSizeLimitError(override val message: String) : RuntimeException(), RepoSizeError

open class FeatureDevSessionContext(val project: Project, val maxProjectSizeBytes: Long? = null) {
    // workspaceContentRoots is all module content root directories of the workspace,
    private val workspaceContentRoots = findWorkspaceContentRoots(project)

    /**
     * workspaceRootDirectory is the concrete directory detected as the project or workspace root:
     *
     * @see addressableRoot
     */
    var workspaceRoot = findWorkspaceRoot(workspaceContentRoots) ?: error("Cannot detect base workspace root")

    private val changeListManager = ChangeListManager.getInstance(project)

    private var _selectionRoot = workspaceRoot

    // This function checks for existence of `devfile.yaml` in customer's repository, currently only `devfile.yaml` is supported for this feature.
    fun checkForDevFile(): Boolean {
        val devFile = File(addressableRoot.toString(), "devfile.yaml")
        return devFile.exists()
    }

    fun getProjectZip(isAutoBuildFeatureEnabled: Boolean?): ZipCreationResult {
        val zippedProject = runBlocking {
            withBackgroundProgress(project, AwsCoreBundle.message("amazonqFeatureDev.placeholder.generating_code")) {
                zipFiles(isAutoBuildFeatureEnabled)
            }
        }
        val checkSum256: String = Base64.getEncoder().encodeToString(DigestUtils.sha256(FileInputStream(zippedProject)))
        return ZipCreationResult(zippedProject, checkSum256, zippedProject.length())
    }

    private suspend fun zipFiles(isAutoBuildFeatureEnabled: Boolean?): File = withContext(getCoroutineBgContext()) {
        val files = mutableListOf<VirtualFile>()
        val ignoredExtensionMap = mutableMapOf<String, Long>().withDefault { 0L }
        var totalSize: Long = 0

        workspaceContentRoots.forEach { contentRoot ->
            VfsUtil.visitChildrenRecursively(
                contentRoot,
                object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
                    override fun visitFile(file: VirtualFile): Boolean {
                        fun markIgnoredContent() {
                            val extension = file.extension.orEmpty()
                            ignoredExtensionMap[extension] = (ignoredExtensionMap[extension] ?: 0) + 1
                        }

                        fun addContent() {
                            if (file.isFile) {
                                totalSize += file.length
                                files.add(file)

                                if (maxProjectSizeBytes != null && totalSize > maxProjectSizeBytes) {
                                    throw RepoSizeLimitError(AwsCoreBundle.message("amazonqFeatureDev.content_length.error_text"))
                                }
                            }
                        }

                        // Always include DevFile if it is enabled and present, taking precedence over other conditions:
                        if (isAutoBuildFeatureEnabled == true && isWorkspaceDevFile(file, addressableRoot)) {
                            addContent()
                            return true
                        }

                        // Large files always ignored:
                        if (file.length > MAX_FILE_SIZE_BYTES) {
                            markIgnoredContent()
                            return false
                        }

                        // Exclude files specified by gitignore or outside the workspace:
                        if (!isWorkspaceSourceContent(file, workspaceContentRoots, changeListManager, additionalGlobalIgnoreRules)) {
                            markIgnoredContent()
                            return false
                        }

                        // Exclude files and directories outside the selection root when working on a subset of the workspace:
                        if (!VfsUtil.isAncestor(selectionRoot, file, false)) {
                            // Because we traverse from the content root, ensure we continue traverse toward the selection root:
                            // (Handles when selection root is inside a content root)
                            if (VfsUtil.isAncestor(file, selectionRoot, false)) {
                                return true
                            }
                            markIgnoredContent()
                            return false
                        }

                        // When auto build is disabled, explicitly exclude devfile:
                        // FIXME: There should be a stronger signal to the agent than presence of the devfile in the uploaded files to enable auto build
                        if (isAutoBuildFeatureEnabled == false && isWorkspaceDevFile(file, addressableRoot)) {
                            markIgnoredContent()
                            return false
                        }

                        addContent()
                        return true
                    }
                }
            )
        }

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
                    val relativePath = VfsUtil.getRelativePath(file, addressableRoot)
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

    /**
     * selectionRoot is the directory selected in replacement of the workspaceRoot when the workspace is too big to bundle for uploading
     *
     * @see addressableRoot
     */
    var selectionRoot: VirtualFile
        set(directory) {
            _selectionRoot = directory
        }
        get() = _selectionRoot

    /**
     * The addressable root of the current working file tree.
     *
     * This property serves as the source of truth for relative paths when:
     * 1. Creating relative paths within zip uploads
     * 2. Resolving paths when downloading from zip
     * 3. Displaying relative paths to users
     *
     * @see addressableRoot
     * @see workspaceRoot
     *
     * Note: Prefer this over workspaceRoot for path operations to maintain consistent path resolution across upload/download/display operations
     * (i.e. We could change from selectionRoot to workspaceRoot here, and these use cases would change behavior in alignment with each other.)
     */

    var addressableRoot: VirtualFile
        get() = selectionRoot
        set(directory) {
            selectionRoot = directory
        }
}

data class ZipCreationResult(val payload: File, val checksum: String, val contentLength: Long)
