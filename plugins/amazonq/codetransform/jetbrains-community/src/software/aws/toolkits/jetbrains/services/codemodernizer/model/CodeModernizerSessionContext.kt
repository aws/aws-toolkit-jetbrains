// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.codemodernizer.EXPLAINABILITY_V1
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCIES_ROOT_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_MANIFEST_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven.TransformMavenRunner
import software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven.runDependencyReportCommands
import software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven.runHilMavenCopyDependency
import software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven.runMavenCopyCommands
import software.aws.toolkits.jetbrains.services.codemodernizer.panels.managers.CodeModernizerBottomWindowPanelManager
import software.aws.toolkits.jetbrains.services.codemodernizer.toolwindow.CodeModernizerBottomToolWindowFactory
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getPathToHilArtifactPomFolder
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getPathToHilDependenciesRootDir
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getPathToHilUploadZip
import software.aws.toolkits.resources.message
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.pathString

const val MANIFEST_PATH = "manifest.json"
const val ZIP_SOURCES_PATH = "sources"
const val ZIP_DEPENDENCIES_PATH = "dependencies"
const val BUILD_LOG_PATH = "build-logs.txt"
const val CUSTOM_DEPENDENCY_VERSIONS_FILE_PATH = "custom-upgrades.yaml"
const val UPLOAD_ZIP_MANIFEST_VERSION = "1.0"
const val HIL_1P_UPGRADE_CAPABILITY = "HIL_1pDependency_VersionUpgrade"
const val EXPLAINABILITY_V1 = "EXPLAINABILITY_V1"
const val CLIENT_SIDE_BUILD = "CLIENT_SIDE_BUILD"
const val MAVEN_CONFIGURATION_FILE_NAME = "pom.xml"
const val MAVEN_BUILD_RUN_UNIT_TESTS = "clean test"
const val MAVEN_BUILD_SKIP_UNIT_TESTS = "clean test-compile"
const val MAVEN_DEFAULT_BUILD_DIRECTORY_NAME = "target"
const val IDEA_DIRECTORY_NAME = ".idea"
const val GIT_DIRECTORY_NAME = ".git"
const val DS_STORE_FILE_NAME = ".DS_Store"
const val INVALID_SUFFIX_SHA = "sha1"
const val INVALID_SUFFIX_REPOSITORIES = "repositories"
const val ORACLE_DB = "ORACLE"
const val AURORA_DB = "AURORA_POSTGRESQL"
const val RDS_DB = "POSTGRESQL"

data class CodeModernizerSessionContext(
    val project: Project,
    var configurationFile: VirtualFile? = null, // used to ZIP module
    val sourceJavaVersion: JavaSdkVersion, // always needed for startJob API
    val targetJavaVersion: JavaSdkVersion, // 17 or 21
    var transformCapabilities: List<String> = listOf(),
    var customBuildCommand: String = MAVEN_BUILD_RUN_UNIT_TESTS, // run unit tests by default
    val sourceVendor: String = ORACLE_DB, // only one supported
    val targetVendor: String? = null,
    val sourceServerName: String? = null,
    var schema: String? = null,
    val sqlMetadataZip: File? = null,
    var customDependencyVersionsFile: VirtualFile? = null,
    var targetJdkName: String? = null,
    var originalUploadZipPath: Path? = null
) : Disposable {
    private val mapper = jacksonObjectMapper()
    private val ignoredDependencyFileExtensions = setOf(INVALID_SUFFIX_SHA, INVALID_SUFFIX_REPOSITORIES)
    private var isDisposed = false
    val mavenRunnerQueue: MutableList<TransformMavenRunner> = mutableListOf()

    private fun File.isMavenTargetFolder(): Boolean {
        val hasPomSibling = this.resolveSibling(MAVEN_CONFIGURATION_FILE_NAME).exists()
        val isMavenTargetDirName = this.isDirectory && this.name == MAVEN_DEFAULT_BUILD_DIRECTORY_NAME
        return isMavenTargetDirName && hasPomSibling
    }

    private fun File.isIdeaFolder(): Boolean = this.isDirectory && this.name == IDEA_DIRECTORY_NAME

    private fun File.isGitFolder(): Boolean = this.isDirectory && this.name == GIT_DIRECTORY_NAME

    private fun findDirectoriesToExclude(sourceFolder: File): List<File> {
        val excluded = mutableListOf<File>()
        sourceFolder.walkTopDown().onEnter {
            if (it.isMavenTargetFolder() || it.isIdeaFolder() || it.isGitFolder()) {
                excluded.add(it)
                return@onEnter false
            }
            return@onEnter true
        }.forEach { _ ->
            // noop, collects the sequence
        }
        return excluded
    }

    fun executeMavenCopyCommands(sourceFolder: File, buildLogBuilder: StringBuilder): MavenCopyCommandsResult {
        if (isDisposed) return MavenCopyCommandsResult.Cancelled
        val shouldSkipTests = customBuildCommand == MAVEN_BUILD_SKIP_UNIT_TESTS
        return runMavenCopyCommands(this, sourceFolder, buildLogBuilder, LOG, project, shouldSkipTests)
    }

    private fun executeHilMavenCopyDependency(sourceFolder: File, destinationFolder: File, buildLogBuilder: StringBuilder) = runHilMavenCopyDependency(
        this,
        sourceFolder,
        destinationFolder,
        buildLogBuilder,
        LOG,
        project,
    )

    fun copyHilDependencyUsingMaven(hilTepDirPath: Path): MavenCopyCommandsResult {
        if (isDisposed) return MavenCopyCommandsResult.Cancelled
        val sourceFolder = File(getPathToHilArtifactPomFolder(hilTepDirPath).pathString)
        val destinationFolder = Files.createDirectories(getPathToHilDependenciesRootDir(hilTepDirPath)).toFile()
        val buildLogBuilder = StringBuilder("Starting Build Log...\n")

        return executeHilMavenCopyDependency(sourceFolder, destinationFolder, buildLogBuilder)
    }

    fun getDependenciesUsingMaven(): MavenCopyCommandsResult {
        if (isDisposed) return MavenCopyCommandsResult.Cancelled
        val root = configurationFile?.parent
        val sourceFolder = File(root?.path)
        val buildLogBuilder = StringBuilder("Starting Build Log...\n")
        return executeMavenCopyCommands(sourceFolder, buildLogBuilder)
    }

    fun createDependencyReportUsingMaven(hilTempPomPath: Path): MavenDependencyReportCommandsResult {
        if (isDisposed) return MavenDependencyReportCommandsResult.Cancelled
        val sourceFolder = File(hilTempPomPath.pathString)
        val buildLogBuilder = StringBuilder("Starting Build Log...\n")
        return executeDependencyVersionReportUsingMaven(sourceFolder, buildLogBuilder)
    }

    private fun executeDependencyVersionReportUsingMaven(
        sourceFolder: File,
        buildLogBuilder: StringBuilder,
    ) = runDependencyReportCommands(this, sourceFolder, buildLogBuilder, LOG, project)

    fun createZipForHilUpload(hilTempPath: Path, manifest: CodeTransformHilDownloadManifest?, targetVersion: String): ZipCreationResult =
        runReadAction {
            try {
                if (manifest == null) {
                    throw CodeModernizerException("No Hil manifest found")
                }

                val depRootPath = getPathToHilDependenciesRootDir(hilTempPath)
                val depDirectory = File(depRootPath.pathString)

                val dependencyFiles = iterateThroughDependencies(depDirectory)

                val depSources = File(HIL_DEPENDENCIES_ROOT_NAME)

                val file = Files.createFile(getPathToHilUploadZip(hilTempPath))
                ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                    // 1) manifest.json
                    mapper.writeValueAsString(
                        CodeTransformHilUploadManifest(
                            hilInput = HilInput(
                                dependenciesRoot = "$HIL_DEPENDENCIES_ROOT_NAME/",
                                pomGroupId = manifest.pomGroupId,
                                pomArtifactId = manifest.pomArtifactId,
                                targetPomVersion = targetVersion,
                            )
                        )
                    )
                        .byteInputStream()
                        .use {
                            zip.putNextEntry(HIL_MANIFEST_FILE_NAME, it)
                        }

                    // 2) Dependencies
                    dependencyFiles.forEach { depFile ->
                        val relativePath = File(depFile.path).relativeTo(depDirectory)
                        val paddedPath = depSources.resolve(relativePath)
                        var paddedPathString = paddedPath.toPath().toString()
                        // Convert Windows file path to work on Linux
                        if (File.separatorChar != '/') {
                            paddedPathString = paddedPathString.replace('\\', '/')
                        }
                        depFile.inputStream().use {
                            zip.putNextEntry(paddedPathString, it)
                        }
                    }
                }

                ZipCreationResult.Succeeded(file.toFile())
            } catch (e: Exception) {
                LOG.error(e) { e.message.toString() }
                throw CodeModernizerException("Unknown exception occurred")
            }
        }

    fun createZipWithModuleFiles(copyResult: MavenCopyCommandsResult?): ZipCreationResult {
        val root = configurationFile?.parent
        val sourceFolder = File(root?.path)
        val buildLogBuilder = StringBuilder("Starting Build Log...\n")
        val depDirectory = if (copyResult is MavenCopyCommandsResult.Success) {
            showTransformationHub()
            copyResult.dependencyDirectory
        } else if (copyResult != null) { // failure cases already handled by now, but to be safe set depDir to null if copyResult failed
            null
        } else {
            sqlMetadataZip // null copyResult means doing a SQL conversion
        }

        return runReadAction {
            try {
                val dirsToExclude = findDirectoriesToExclude(sourceFolder)
                val files = root?.let {
                    VfsUtil.collectChildrenRecursively(it).filter { child ->
                        val childPath = Path(child.path)
                        !child.isDirectory && !child.name.endsWith(DS_STORE_FILE_NAME) && dirsToExclude.none { dir -> childPath.startsWith(dir.toPath()) }
                    }
                }
                val dependencyFiles = if (depDirectory != null) {
                    iterateThroughDependencies(depDirectory)
                } else {
                    mutableListOf()
                }

                val zipSources = File(ZIP_SOURCES_PATH)
                val depSources = File(ZIP_DEPENDENCIES_PATH)
                val outputFile = createTemporaryZipFile { zip ->
                    // 1) Manifest file
                    var manifest = ZipManifest(transformCapabilities = transformCapabilities, customBuildCommand = customBuildCommand)
                    if (sqlMetadataZip != null) {
                        // doing a SQL conversion, not language upgrade
                        val sctFileName = sqlMetadataZip.listFiles { file -> file.name.endsWith(".sct") }.first().name
                        manifest = ZipManifest(
                            requestedConversions = RequestedConversions(
                                SQLConversion(sourceVendor, targetVendor, schema, sourceServerName, sctFileName)
                            )
                        )
                    }
                    mapper.writeValueAsString(manifest)
                        .byteInputStream()
                        .use {
                            zip.putNextEntry(Path(MANIFEST_PATH).toString(), it)
                        }

                    // 2) Dependencies / SQL conversion metadata
                    if (depDirectory != null) {
                        dependencyFiles.forEach { depFile ->
                            val relativePath = File(depFile.path).relativeTo(depDirectory)
                            val paddedPath = depSources.resolve(relativePath)
                            var paddedPathString = paddedPath.toPath().toString()
                            // Convert Windows file path to work on Linux
                            if (File.separatorChar != '/') {
                                paddedPathString = paddedPathString.replace('\\', '/')
                            }
                            depFile.inputStream().use {
                                zip.putNextEntry(paddedPathString, it)
                            }
                        }
                    }

                    LOG.info { "Dependency files size = ${dependencyFiles.sumOf { it.length().toInt() }}" }

                    // 3) Custom YAML file
                    // TODO: where to put this? VS Code puts it in custom-upgrades/dependency-versions.yaml; here we put it at the root
                    if (customDependencyVersionsFile != null) {
                        customDependencyVersionsFile?.inputStream?.use {
                            zip.putNextEntry(Path(CUSTOM_DEPENDENCY_VERSIONS_FILE_PATH).toString(), it)
                        }
                    }

                    // 4) Sources
                    files?.forEach { file ->
                        val relativePath = File(file.path).relativeTo(sourceFolder)
                        val paddedPath = zipSources.resolve(relativePath)
                        var paddedPathString = paddedPath.toPath().toString()
                        // Convert Windows file path to work on Linux
                        if (File.separatorChar != '/') {
                            paddedPathString = paddedPathString.replace('\\', '/')
                        }
                        try {
                            file.inputStream.use {
                                zip.putNextEntry(paddedPathString, it)
                            }
                        } catch (e: NoSuchFileException) {
                            // continue without failing
                            LOG.error { "NoSuchFileException likely due to a symlink, skipping file" }
                        }
                    }

                    LOG.info { "Source code files size = ${files?.sumOf { it.length.toInt() }}" }

                    // 5) Initial Maven copy-deps / install build log
                    buildLogBuilder.toString().byteInputStream().use {
                        zip.putNextEntry(Path(BUILD_LOG_PATH).toString(), it)
                    }
                }.toFile()
                // depDirectory should never be null
                if (depDirectory != null) ZipCreationResult.Succeeded(outputFile) else ZipCreationResult.Missing1P(outputFile)
            } catch (e: NoSuchFileException) {
                throw CodeModernizerException("Source folder not found")
            } catch (e: Exception) {
                LOG.error(e) { e.message.toString() }
                throw CodeModernizerException("Unknown exception occurred")
            } finally {
                depDirectory?.deleteRecursively()
            }
        }
    }

    private fun Path.isIgnoredDependency() = this.toFile().extension in ignoredDependencyFileExtensions

    fun iterateThroughDependencies(depDirectory: File): MutableList<File> {
        val dependencyFiles = mutableListOf<File>()
        Files.walkFileTree(
            depDirectory.toPath(),
            setOf(FileVisitOption.FOLLOW_LINKS),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(path: Path, attrs: BasicFileAttributes?): FileVisitResult {
                    if (!path.isIgnoredDependency()) {
                        dependencyFiles.add(path.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult =
                    FileVisitResult.CONTINUE
            }
        )
        return dependencyFiles
    }

    fun showTransformationHub() = runInEdt {
        val appModernizerBottomWindow = ToolWindowManager.getInstance(project).getToolWindow(CodeModernizerBottomToolWindowFactory.id)
            ?: error(message("codemodernizer.toolwindow.problems_window_not_found"))
        appModernizerBottomWindow.show()
        CodeModernizerBottomWindowPanelManager.getInstance(project).setJobStartingUI()
    }

    override fun dispose() {
        isDisposed = true
        this.mavenRunnerQueue.forEach {
            it.cancel()
        }
    }

    companion object {
        private val LOG = getLogger<CodeModernizerSessionContext>()
    }
}
