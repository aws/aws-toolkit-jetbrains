// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.slf4j.Logger
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeTransformTelemetryManager
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenDependencyReportCommandsResult
import software.aws.toolkits.jetbrains.utils.notifyStickyInfo
import software.aws.toolkits.telemetry.CodeTransformBuildCommand
import software.aws.toolkits.telemetry.Result
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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

fun runMavenCopyCommands(
    context: CodeModernizerSessionContext,
    sourceFolder: File,
    logBuilder: StringBuilder,
    logger: Logger,
    project: Project,
    shouldSkipTests: Boolean,
): MavenCopyCommandsResult {
    val currentTimestamp = System.currentTimeMillis()
    val destinationDir = Files.createTempDirectory("transformation_dependencies_temp_$currentTimestamp")
    val telemetry = CodeTransformTelemetryManager.getInstance(project)
    var telemetryErrorMessage = ""
    var telemetryLocalBuildResult = Result.Succeeded

    logger.info { "Executing IntelliJ bundled Maven" }
    try {
        // Create shared parameters
        val transformMvnRunner = TransformMavenRunner(project)
        context.mavenRunnerQueue.add(transformMvnRunner)
        val mvnSettings = MavenRunner.getInstance(project).settings.clone() // clone required to avoid editing user settings
        notifyStickyInfo("current jreName", mvnSettings.jreName)
        mvnSettings.setJreName("corretto-21")
        // use ProjectJdkTable.getInstance().allJdks to join all of the "name"s of the list
        val jreNames = ProjectJdkTable.getInstance().allJdks.joinToString(", ") { it.name }
        notifyStickyInfo("jreNames", jreNames)

        val sourceVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(sourceFolder)
        val module = sourceVirtualFile?.let { ModuleUtilCore.findModuleForFile(it, project) }
        val moduleSdk = module?.let { ModuleRootManager.getInstance(it).sdk }
        val sdk = moduleSdk ?: ProjectRootManager.getInstance(project).projectSdk
        // edge case: module SDK and project SDK are null, and Maven Runner Settings is using the null project SDK, so Maven Runner will definitely fail
        if (sdk == null && mvnSettings.jreName == "#USE_PROJECT_JDK") return MavenCopyCommandsResult.NoJdk

        // run copy dependencies
        val copyDependenciesRunnable =
            runMavenCopyDependencies(sourceFolder, logBuilder, mvnSettings, transformMvnRunner, destinationDir, logger)
        copyDependenciesRunnable.await()
        logBuilder.appendLine(copyDependenciesRunnable.getOutput())
        if (copyDependenciesRunnable.isComplete()) {
            val successMsg = "IntelliJ IDEA bundled Maven copy-dependencies executed successfully"
            logger.info { successMsg }
            logBuilder.appendLine(successMsg)
        } else if (copyDependenciesRunnable.isTerminated()) {
            telemetryLocalBuildResult = Result.Cancelled
            return MavenCopyCommandsResult.Cancelled
        } else {
            telemetryErrorMessage += "Maven Copy: bundled Maven failed. "
        }

        // Run clean
        val cleanRunnable = runMavenClean(sourceFolder, logBuilder, mvnSettings, transformMvnRunner, logger, destinationDir)
        cleanRunnable.await()
        logBuilder.appendLine(cleanRunnable.getOutput())
        if (cleanRunnable.isComplete()) {
            val successMsg = "IntelliJ bundled Maven clean executed successfully"
            logger.info { successMsg }
            logBuilder.appendLine(successMsg)
        } else if (cleanRunnable.isTerminated()) {
            telemetryLocalBuildResult = Result.Cancelled
            return MavenCopyCommandsResult.Cancelled
        } else {
            telemetryErrorMessage += "Maven Clean: bundled Maven failed."

            telemetryLocalBuildResult = Result.Failed
            return MavenCopyCommandsResult.Failure
        }

        // Run install
        val installRunnable = runMavenInstall(sourceFolder, logBuilder, mvnSettings, transformMvnRunner, logger, destinationDir, shouldSkipTests)
        installRunnable.await()
        logBuilder.appendLine(installRunnable.getOutput())
        if (installRunnable.isComplete()) {
            val successMsg = "IntelliJ bundled Maven install executed successfully"
            logger.info { successMsg }
            logBuilder.appendLine(successMsg)
        } else if (installRunnable.isTerminated()) {
            telemetryLocalBuildResult = Result.Cancelled
            return MavenCopyCommandsResult.Cancelled
        } else {
            telemetryErrorMessage += "Maven Install: bundled Maven failed."

            telemetryLocalBuildResult = Result.Failed
            return MavenCopyCommandsResult.Failure
        }
    } catch (t: Throwable) {
        val errorMessage = "IntelliJ bundled Maven executed failed: ${t.message}"
        logger.error(t) { errorMessage }
        telemetryErrorMessage = errorMessage
        telemetryLocalBuildResult = Result.Failed
        return MavenCopyCommandsResult.Failure
    } finally {
        // emit telemetry
        telemetry.localBuildProject(CodeTransformBuildCommand.IDEBundledMaven, telemetryLocalBuildResult, telemetryErrorMessage)
    }
    // When all commands executed successfully, show the transformation hub
    return MavenCopyCommandsResult.Success(destinationDir.toFile())
}

private fun runMavenCopyDependencies(
    sourceFolder: File,
    buildlogBuilder: StringBuilder,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    destinationDir: Path,
    logger: Logger,
): TransformRunnable {
    buildlogBuilder.appendLine("Command Run: IntelliJ IDEA bundled Maven dependency:copy-dependencies")
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
            buildlogBuilder.appendLine("IntelliJ bundled Maven copy dependencies failed: ${t.message}")
        }
    }
    return copyTransformRunnable
}

private fun runMavenClean(
    sourceFolder: File,
    buildlogBuilder: StringBuilder,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    logger: Logger,
    destinationDir: Path,
): TransformRunnable {
    buildlogBuilder.appendLine("Command Run: IntelliJ IDEA bundled Maven clean")
    val cleanParams = MavenRunnerParameters(
        false,
        sourceFolder.absolutePath,
        null,
        listOf("-Dmaven.repo.local=$destinationDir", "clean"),
        emptyList<String>(),
        null
    )
    val cleanTransformRunnable = TransformRunnable()
    runInEdt {
        try {
            transformMavenRunner.run(cleanParams, mvnSettings, cleanTransformRunnable)
        } catch (t: Throwable) {
            logger.error { "Maven Clean: Unexpected error when executing bundled Maven clean" }
            cleanTransformRunnable.setExitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
            buildlogBuilder.appendLine("IntelliJ bundled Maven clean failed: ${t.message}")
        }
    }
    return cleanTransformRunnable
}

private fun runMavenInstall(
    sourceFolder: File,
    logBuilder: StringBuilder,
    mvnSettings: MavenRunnerSettings,
    transformMavenRunner: TransformMavenRunner,
    logger: Logger,
    destinationDir: Path,
    shouldSkipTests: Boolean,
): TransformRunnable {
    logBuilder.appendLine("Command Run: IntelliJ IDEA bundled Maven install")
    val flags = if (shouldSkipTests) {
        listOf("-Dmaven.repo.local=$destinationDir", "install", "-DskipTests")
    } else {
        listOf("-Dmaven.repo.local=$destinationDir", "install")
    }
    val installParams = MavenRunnerParameters(
        false,
        sourceFolder.absolutePath,
        null,
        flags,
        emptyList<String>(),
        null
    )
    val installTransformRunnable = TransformRunnable()
    runInEdt {
        try {
            transformMavenRunner.run(installParams, mvnSettings, installTransformRunnable)
        } catch (t: Throwable) {
            logger.error(t) { "Maven Install: Unexpected error when executing bundled Maven install" }
            installTransformRunnable.setExitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
            logBuilder.appendLine("IntelliJ bundled Maven install failed: ${t.message}")
        }
    }
    return installTransformRunnable
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
        emptyList<String>(),
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
