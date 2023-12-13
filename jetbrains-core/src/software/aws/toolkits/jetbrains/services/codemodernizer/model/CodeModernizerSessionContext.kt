// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.isNotEmptyDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleProject
import org.jetbrains.plugins.gradle.settings.GradleSettings
import software.aws.toolkits.core.utils.*
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeTransformTelemetryState
import software.aws.toolkits.telemetry.CodeTransformMavenBuildCommand
import software.aws.toolkits.telemetry.CodetransformTelemetry
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import kotlin.io.NoSuchFileException
import kotlin.io.byteInputStream
import kotlin.io.deleteRecursively
import kotlin.io.inputStream
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.relativeTo
import kotlin.io.resolve
import kotlin.io.resolveSibling
import kotlin.io.walkTopDown


const val MANIFEST_PATH = "manifest.json"
const val ZIP_SOURCES_PATH = "sources"
const val ZIP_DEPENDENCIES_PATH = "dependencies"
const val MAVEN_CONFIGURATION_FILE_NAME = "pom.xml"
const val MAVEN_DEFAULT_BUILD_DIRECTORY_NAME = "target"
const val IDEA_DIRECTORY_NAME = ".idea"
data class CodeModernizerSessionContext(
    val project: Project,
    val configurationFile: VirtualFile,
    val sourceJavaVersion: JavaSdkVersion,
    val targetJavaVersion: JavaSdkVersion,
) {
    private val mapper = jacksonObjectMapper()

    fun File.isMavenTargetFolder(): Boolean {
        val hasPomSibling = this.resolveSibling(MAVEN_CONFIGURATION_FILE_NAME).exists()
        val isMavenTargetDirName = this.isDirectory && this.name == MAVEN_DEFAULT_BUILD_DIRECTORY_NAME
        return isMavenTargetDirName && hasPomSibling
    }

    fun File.isIdeaFolder(): Boolean {
        val isIdea = this.isDirectory && this.name == IDEA_DIRECTORY_NAME
        return isIdea
    }

    /**
     * TODO use an approach based on walkTopDown instead of VfsUtil.collectChildrenRecursively(root) in createZipWithModuleFiles.
     * We now recurse the file tree twice and then filter which hurts performance for large projects.
     */
    private fun findDirectoriesToExclude(sourceFolder: File): List<File> {
        val excluded = mutableListOf<File>()
        sourceFolder.walkTopDown().onEnter {
            if (it.isMavenTargetFolder() || it.isIdeaFolder()) {
                excluded.add(it)
                return@onEnter false
            }
            return@onEnter true
        }.forEach {
            // noop, collects the sequence
        }
        return excluded
    }

    suspend fun createZipWithModuleFiles(): ZipCreationResult {
        val root = configurationFile.parent
        val sourceFolder = File(root.path)
        val depDirectory = runMavenCommand(sourceFolder)
        if (depDirectory != null) {
            CodetransformTelemetry.dependenciesCopied(
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            )
        }
        return runReadAction {
            try {
                val directoriesToExclude = findDirectoriesToExclude(sourceFolder)
                val files = VfsUtil.collectChildrenRecursively(root).filter { child ->
                    val childPath = Path(child.path)
                    !child.isDirectory && directoriesToExclude.none { childPath.startsWith(it.toPath()) }
                }
                val dependencyfiles = if (depDirectory != null) {
                    iterateThroughDependencies(depDirectory)
                } else {
                    mutableListOf()
                }

                val dependenciesRoot = if (depDirectory != null) "$ZIP_DEPENDENCIES_PATH/${depDirectory.name}" else null
                val zipManifest = mapper.writeValueAsString(ZipManifest(dependenciesRoot = dependenciesRoot)).byteInputStream()

                val zipSources = File(ZIP_SOURCES_PATH)
                val depSources = File(ZIP_DEPENDENCIES_PATH)
                val outputFile = createTemporaryZipFile {
                    it.putNextEntry(Path(MANIFEST_PATH).toString(), zipManifest)
                    if (depDirectory != null) {
                        dependencyfiles.forEach { depfile ->
                            val relativePath = File(depfile.path).relativeTo(depDirectory.parentFile)
                            val paddedPath = depSources.resolve(relativePath)
                            it.putNextEntry(paddedPath.toPath().toString(), depfile.inputStream())
                        }
                    }
                    files.forEach { file ->
                        val relativePath = File(file.path).relativeTo(sourceFolder)
                        val paddedPath = zipSources.resolve(relativePath)
                        it.putNextEntry(paddedPath.toPath().toString(), file.inputStream)
                    }
                }.toFile()
                if (depDirectory != null) ZipCreationResult.Succeeded(outputFile) else ZipCreationResult.Missing1P(outputFile)
            } catch (e: NoSuchFileException) {
                throw CodeModernizerException("Source folder not found: ${root.path}")
            } catch (e: Exception) {
                LOG.error(e) { e.message.toString() }
                throw CodeModernizerException("Unknown exception occurred ${root.path}")
            } finally {
                depDirectory?.deleteRecursively()
            }
        }
    }

    /**
     * @description
     * this command is used to run the maven commmand which copies all the dependencies to a temp file which we will use to zip our own files to
     */
    suspend fun runMavenCommand(sourceFolder: File): File? {
        val currentTimestamp = System.currentTimeMillis()
        val destinationDir = Files.createTempDirectory("transformation_dependencies_temp_" + currentTimestamp)
        fun runCommand(mavenCommand: String): ProcessOutput {
            val commandLine = GeneralCommandLine(
                mavenCommand,
                "dependency:copy-dependencies",
                "-DoutputDirectory=$destinationDir",
                "-Dmdep.useRepositoryLayout=true",
                "-Dmdep.copyPom=true",
                "-Dmdep.addParentPoms=true"
            )
                .withWorkDirectory(sourceFolder)
                .withRedirectErrorStream(true)
            val output = ExecUtil.execAndGetOutput(commandLine)
            return output
        }

        // 1. Try to execute Maven Wrapper Command
        LOG.warn { "Executing ./mvnw" }
        var shouldTryMvnCommand = true
        try {
            val output = runCommand("./mvnw")
            if (output.exitCode != 0) {
                LOG.error { "mvnw command output:\n$output" }
                val error = "The exitCode should be 0 while it was ${output.exitCode}"
                CodetransformTelemetry.mvnBuildFailed(
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Mvnw,
                    reason = error
                )
                return null
            } else {
                LOG.warn { "mvnw executed successfully" }
                shouldTryMvnCommand = false
            }
        } catch (e: ProcessNotCreatedException) {
            val error = "./mvnw failed to execute as its likely not a unix machine"
            CodetransformTelemetry.mvnBuildFailed(
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Mvnw,
                reason = error
            )
            LOG.warn { error }
        } catch (e: Exception) {
            CodetransformTelemetry.mvnBuildFailed(
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Mvnw,
                reason = e.message
            )
            when {
                e.message?.contains("Cannot run program \"./mvnw\"") == true -> {} // noop
                else -> throw e
            }
        }

        // 2. maybe execute maven wrapper command
        if (shouldTryMvnCommand) {
            LOG.warn { "Executing mvn" }
            try {
                val output = runCommand("mvn")
                if (output.exitCode != 0) {
                    LOG.error { "Maven command output:\n$output" }
                    val error = "The exitCode should be 0 while it was ${output.exitCode}"
                    CodetransformTelemetry.mvnBuildFailed(
                        codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                        codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Mvn,
                        reason = error
                    )
                    return null
                } else {
                    shouldTryMvnCommand = false
                    LOG.warn { "Maven executed successfully" }
                }
            } catch (e: ProcessNotCreatedException) {
                val error = "Maven failed to execute as its likely not installed to the PATH"
                CodetransformTelemetry.mvnBuildFailed(
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Mvn,
                    reason = error
                )
                LOG.warn { error }
            } catch (e: Exception) {
                CodetransformTelemetry.mvnBuildFailed(
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Mvn,
                    reason = e.message
                )
                LOG.error(e) { e.message.toString() }
            }
        }

        // 3. intellij-bundled maven runner
        if (shouldTryMvnCommand) {
            LOG.warn { "Executing IntelliJ bundled Maven" }
            val goalcp = "dependency:copy-dependencies"
            val outputDirectory = "-DoutputDirectory=$destinationDir"
            val repolay = "-Dmdep.useRepositoryLayout=true"
            val pomcp = "-Dmdep.copyPom=true"
            val parentpom = "-Dmdep.addParentPoms=true"
            val commandlist = mutableListOf<String>()
            val explicitenabled = mutableListOf<String>()
            commandlist.add(goalcp)
            commandlist.add(outputDirectory)
            commandlist.add(repolay)
            commandlist.add(pomcp)
            commandlist.add(parentpom)
            val params = MavenRunnerParameters(
                false,
                project.basePath.toString(),
                null,
                commandlist,
                explicitenabled,
                null
            )

            // Create MavenRunnerParametersMavenRunnerParameters
            val mvnrunner = MavenRunner.getInstance(project)
            val transfromMvnRunner = TransformMavenRunner(mvnrunner, project)
            val mvnsettings = mvnrunner.settings
            var createdDependencies = TransformRunnable()
            try {
                runInEdt {
                    transfromMvnRunner.run(params, mvnsettings, createdDependencies)
                }
                while (createdDependencies.isComplete() == null) {
                    // waiting mavenrunner building
                    delay(50)
                }
                if (createdDependencies.isComplete() == 0) {
                    LOG.warn { "IntelliJ bundled Maven executed successfully" }
                } else {
                    val error = "The exitCode should be 0 while it was ${createdDependencies.isComplete()}"
                    LOG.error { error }
//                    CodetransformTelemetry.mvnBuildFailed(
//                        codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
//                        codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Intellij_bundled_maven,
//                        reason = error
//                    )
                    return null
                }
            } catch (e: Exception) {
                LOG.error(e) { e.message.toString() }
//                CodetransformTelemetry.mvnBuildFailed(
//                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
//                    codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.Intellij_bundled_maven,
//                    reason = e.message
//                )
                throw e
            }
        }

        return destinationDir.toFile()
    }

    private fun iterateThroughDependencies(depDirectory: File): MutableList<File> {
        val dependencyfiles = mutableListOf<File>()
        Files.walkFileTree(
            depDirectory.toPath(),
            setOf(FileVisitOption.FOLLOW_LINKS),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file != null) {
                        dependencyfiles.add(file.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult =
                    FileVisitResult.CONTINUE
            }
        )
        return dependencyfiles
    }

    companion object {
        private val LOG = getLogger<CodeModernizerSessionContext>()
    }
}

