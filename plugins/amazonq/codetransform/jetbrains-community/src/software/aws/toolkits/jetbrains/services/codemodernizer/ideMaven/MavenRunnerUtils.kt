// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.slf4j.Logger
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.info
import software.amazon.q.jetbrains.AwsPlugin
import software.amazon.q.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeTransformTelemetryManager
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_BUILD_RUN_UNIT_TESTS
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenDependencyReportCommandsResult
import software.aws.toolkits.telemetry.CodeTransformBuildCommand
import software.aws.toolkits.telemetry.Result
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun runMavenCopyCommands(
    context: CodeModernizerSessionContext,
    sourceFolder: File,
    logBuilder: StringBuilder,
    logger: Logger,
    project: Project,
): MavenCopyCommandsResult {
    val currentTimestamp = System.currentTimeMillis()
    val destinationDir = Files.createTempDirectory("transformation_dependencies_temp_$currentTimestamp")
    val telemetry = CodeTransformTelemetryManager.getInstance(project)
    var telemetryErrorMessage = ""
    var telemetryLocalBuildResult = Result.Succeeded
    logger.info { "Executing IntelliJ bundled Maven" }
    try {
        val transformMvnRunner = TransformMavenRunner(project)
        context.mavenRunnerQueue.add(transformMvnRunner)
        val mvnSettings = MavenRunner.getInstance(project).settings.clone() // clone required to avoid editing user settings
        val sourceVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(sourceFolder)
        val module = sourceVirtualFile?.let { ModuleUtilCore.findModuleForFile(it, project) }
        val moduleSdk = module?.let { ModuleRootManager.getInstance(it).sdk }
        val sdk = moduleSdk ?: ProjectRootManager.getInstance(project).projectSdk
        // edge case: module SDK and project SDK are null, and Maven Runner Settings is using the null project SDK, so Maven Runner will definitely fail
        if (sdk == null && mvnSettings.jreName == "#USE_PROJECT_JDK") return MavenCopyCommandsResult.NoJdk
        // run clean test-compile with Maven JAR
        val jarRunnable = runMavenJar(sourceFolder, logBuilder, mvnSettings, transformMvnRunner, destinationDir, logger)
        jarRunnable.await()
        val output = jarRunnable.getOutput()?.lowercase()?.replace("elasticgumby", "QCT")
        logBuilder.appendLine(output)
        if (jarRunnable.isComplete()) {
            val successMsg = "IntelliJ bundled Maven JAR executed successfully"
            logger.info { successMsg }
            logBuilder.appendLine(successMsg)
        } else if (jarRunnable.isTerminated()) {
            telemetryLocalBuildResult = Result.Cancelled
            return MavenCopyCommandsResult.Cancelled
        } else {
            telemetryErrorMessage += "Maven JAR: bundled Maven failed."
            telemetryLocalBuildResult = Result.Failed
            return MavenCopyCommandsResult.Failure
        }
    } catch (t: Throwable) {
        val error = t.message?.lowercase()?.replace("elasticgumby", "QCT")
        val errorMessage = "IntelliJ bundled Maven JAR executed failed: $error"
        logger.error(t) { errorMessage }
        telemetryErrorMessage = errorMessage
        telemetryLocalBuildResult = Result.Failed
        return MavenCopyCommandsResult.Failure
    } finally {
        telemetry.localBuildProject(CodeTransformBuildCommand.IDEBundledMaven, telemetryLocalBuildResult, telemetryErrorMessage)
    }
    return MavenCopyCommandsResult.Success(destinationDir.toFile())
}

private fun runMavenJar(
    sourceFolder: File,
    logBuilder: StringBuilder,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    destinationDir: Path,
    logger: Logger,
): TransformRunnable {
    logBuilder.appendLine("Command Run: IntelliJ IDEA bundled Maven JAR")
    val jarPath = AwsToolkit.PLUGINS_INFO[AwsPlugin.Q]?.path?.resolve("lib/QCT-Maven-1-0-156-0.jar")

    val commandList = listOf(
        "-Dmaven.ext.class.path=$jarPath",
        "-Dcom.amazon.aws.developer.transform.jobDirectory=$destinationDir",
        "clean",
        "test-compile"
    )
    val jarParams = MavenRunnerParameters(
        false,
        sourceFolder.absolutePath,
        null,
        commandList,
        emptyList<String>(),
        null
    )
    val jarRunnable = TransformRunnable()
    runInEdt {
        try {
            transformMavenRunner.run(jarParams, mvnSettings, jarRunnable)
        } catch (t: Throwable) {
            val error = "Maven JAR: Unexpected error when executing bundled Maven JAR"
            jarRunnable.setExitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
            logger.info(t) { error }
            logBuilder.appendLine("IntelliJ bundled Maven JAR failed: ${t.message}")
        }
    }
    return jarRunnable
}

fun runClientSideBuild(targetDir: VirtualFile, logger: Logger, project: Project): Pair<Int?, String> {
    // run mvn test-compile or mvn test
    val transformMvnRunner = TransformMavenRunner(project)
    val mvnSettings = MavenRunner.getInstance(project).settings.clone()
    val buildRunnable = runClientSideBuild(targetDir, mvnSettings, transformMvnRunner, logger, project)
    buildRunnable.await()
    // write build output to a new text file and open it
    val buildLogOutput = buildRunnable.getOutput().toString()
    // first line is a long Maven String showing the build command; not useful or needed
    val outputWithoutFirstLine = buildLogOutput.lines().drop(1).joinToString("\n")
    val buildLogOutputFile = Files.createTempFile("build-logs-", ".txt")
    Files.write(buildLogOutputFile, outputWithoutFirstLine.toByteArray())
    val buildLogOutputVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(buildLogOutputFile.toFile())
    runInEdt {
        if (buildLogOutputVirtualFile != null) {
            FileEditorManager.getInstance(project).openFile(buildLogOutputVirtualFile, true)
        }
    }
    return buildRunnable.getExitCode() to buildRunnable.getOutput().toString()
}

private fun runClientSideBuild(
    targetDir: VirtualFile,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    logger: Logger,
    project: Project,
): TransformRunnable {
    val customBuildCommand = CodeModernizerManager.getInstance(project).codeTransformationSession?.sessionContext?.customBuildCommand
    val clientSideBuildCommand = if (customBuildCommand == MAVEN_BUILD_RUN_UNIT_TESTS) "test" else "test-compile"
    val buildParams = MavenRunnerParameters(
        false,
        targetDir.path,
        null,
        listOf(clientSideBuildCommand),
        null,
        null
    )
    val buildTransformRunnable = TransformRunnable()
    runInEdt {
        try {
            CodeModernizerManager.getInstance(project).getMvnBuildWindow().show()
            transformMavenRunner.run(buildParams, mvnSettings, buildTransformRunnable, true)
        } catch (t: Throwable) {
            logger.error(t) { "Maven Build: Unexpected error when executing bundled Maven $clientSideBuildCommand" }
            buildTransformRunnable.setExitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
        }
    }
    return buildTransformRunnable
}

// TODO: all functions below are for HIL; consider removing once client-side build released

fun runHilMavenCopyDependency(
    context: CodeModernizerSessionContext,
    sourceFolder: File,
    destinationDir: File,
    logBuilder: StringBuilder,
    logger: Logger,
    project: Project,
): MavenCopyCommandsResult {
    logger.info { "Executing IntelliJ bundled Maven" }
    try {
        // Create shared parameters
        val transformMvnRunner = TransformMavenRunner(project)
        context.mavenRunnerQueue.add(transformMvnRunner)
        val mvnSettings = MavenRunner.getInstance(project).settings.clone() // clone required to avoid editing user settings

        // run copy dependencies
        val copyDependenciesRunnable =
            runMavenCopyDependencies(sourceFolder, logBuilder, mvnSettings, transformMvnRunner, destinationDir.toPath(), logger)
        copyDependenciesRunnable.await()
        logBuilder.appendLine(copyDependenciesRunnable.getOutput())
        if (copyDependenciesRunnable.isComplete()) {
            val successMsg = "IntelliJ IDEA bundled Maven copy-dependencies executed successfully"
            logger.info { successMsg }
            logBuilder.appendLine(successMsg)
        } else if (copyDependenciesRunnable.isTerminated()) {
            return MavenCopyCommandsResult.Cancelled
        }
    } catch (t: Throwable) {
        logger.error(t) { "IntelliJ bundled Maven copy-dependencies failed" }
        return MavenCopyCommandsResult.Failure
    }
    // When all commands executed successfully, show the transformation hub
    return MavenCopyCommandsResult.Success(destinationDir)
}

private fun runMavenCopyDependencies(
    sourceFolder: File,
    logBuilder: StringBuilder,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    destinationDir: Path,
    logger: Logger,
): TransformRunnable {
    logBuilder.appendLine("Command Run: IntelliJ IDEA bundled Maven dependency:copy-dependencies")
    val copyCommandList = listOf(
        "dependency:copy-dependencies",
        "-DoutputDirectory=$destinationDir",
        "-Dmdep.useRepositoryLayout=true",
        "-Dmdep.copyPom=true",
        "-Dmdep.addParentPoms=true",
    )
    val copyParams = MavenRunnerParameters(
        false,
        sourceFolder.absolutePath,
        null,
        copyCommandList,
        emptyList<String>(),
        null
    )
    val copyTransformRunnable = TransformRunnable()
    runInEdt {
        try {
            transformMavenRunner.run(copyParams, mvnSettings, copyTransformRunnable)
        } catch (t: Throwable) {
            val error = "Maven Copy: Unexpected error when executing bundled Maven copy dependencies"
            copyTransformRunnable.setExitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
            logger.info(t) { error }
            logBuilder.appendLine("IntelliJ bundled Maven copy dependencies failed: ${t.message}")
        }
    }
    return copyTransformRunnable
}

private fun runMavenDependencyUpdatesReport(
    sourceFolder: File,
    logBuilder: StringBuilder,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    logger: Logger,
): TransformRunnable {
    logBuilder.appendLine("Command Run: IntelliJ IDEA bundled Maven dependency updates report")

    val dependencyUpdatesReportCommandList = listOf(
        "versions:dependency-updates-aggregate-report",
        "-DonlyProjectDependencies=true",
        "-DdependencyUpdatesReportFormats=xml",
    )

    val params = MavenRunnerParameters(
        false,
        sourceFolder.absolutePath,
        null,
        dependencyUpdatesReportCommandList,
        null,
        null
    )
    val dependencyUpdatesReportRunnable = TransformRunnable()
    runInEdt {
        try {
            transformMavenRunner.run(params, mvnSettings, dependencyUpdatesReportRunnable)
        } catch (t: Throwable) {
            logger.error(t) { "Maven dependency report: Unexpected error when executing bundled Maven dependency updates report" }
            dependencyUpdatesReportRunnable.setExitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
            logBuilder.appendLine("IntelliJ bundled Maven dependency updates report failed: ${t.message}")
        }
    }
    return dependencyUpdatesReportRunnable
}

fun runDependencyReportCommands(
    context: CodeModernizerSessionContext,
    sourceFolder: File,
    logBuilder: StringBuilder,
    logger: Logger,
    project: Project,
): MavenDependencyReportCommandsResult {
    logger.info { "Executing IntelliJ bundled Maven" }

    val transformMvnRunner = TransformMavenRunner(project)
    context.mavenRunnerQueue.add(transformMvnRunner)
    val mvnSettings = MavenRunner.getInstance(project).settings.clone() // clone required to avoid editing user settings

    val runnable = runMavenDependencyUpdatesReport(sourceFolder, logBuilder, mvnSettings, transformMvnRunner, logger)
    runnable.await()
    logBuilder.appendLine(runnable.getOutput())
    if (runnable.isComplete()) {
        val successMsg = "IntelliJ bundled Maven dependency report executed successfully"
        logger.info { successMsg }
        logBuilder.appendLine(successMsg)
    } else if (runnable.isTerminated()) {
        return MavenDependencyReportCommandsResult.Cancelled
    } else {
        return MavenDependencyReportCommandsResult.Failure
    }

    return MavenDependencyReportCommandsResult.Success
}
