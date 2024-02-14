// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven

import ai.grazie.utils.firstOrError
import com.intellij.externalSystem.JavaProjectData
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.getAvailableJdk
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.MavenRunnerSettings.USE_JAVA_HOME
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeTransformTelemetryState
import software.aws.toolkits.telemetry.CodeTransformMavenBuildCommand
import software.aws.toolkits.telemetry.CodetransformTelemetry
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class MavenRunManager(val project: Project) {
    fun emitMavenFailure(error: String, throwable: Throwable? = null){
        if(throwable != null) LOG.error(throwable) { error } else LOG.error { error }
        CodetransformTelemetry.mvnBuildFailed(
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.IDEBundledMaven,
            reason = error
        )
    }

    fun runMavenClean(sourceFolder: File, buildlogBuilder: StringBuilder, mvnSettings: MavenRunnerSettings, transformMavenRunner: TransformMavenRunner): TransformRunnable {
        buildlogBuilder.appendLine("Command Run: IntelliJ bundled Maven clean")
        val cleanParams = MavenRunnerParameters(
            false,
            sourceFolder.absolutePath,
            null,
            listOf("clean", "-q"),
            emptyList<String>(),
            null
        )
        val cleanTransformRunnable = TransformRunnable()
        runInEdt {
            try {
                transformMavenRunner.run(cleanParams, mvnSettings, cleanTransformRunnable)
            } catch (t: Throwable) {
                val error = "Maven Clean: Unexpected error when executing bundled Maven clean"
                cleanTransformRunnable.exitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
                buildlogBuilder.appendLine("IntelliJ bundled Maven clean failed: ${t.message}")
                emitMavenFailure(error, t)
            }
        }
        return cleanTransformRunnable
    }

    fun runMavenInstall(sourceFolder: File, buildlogBuilder: StringBuilder, mvnSettings: MavenRunnerSettings, transformMavenRunner: TransformMavenRunner): TransformRunnable {
        buildlogBuilder.appendLine("Command Run: IntelliJ bundled Maven install")
        val installParams = MavenRunnerParameters(
            false,
            sourceFolder.absolutePath,
            null,
            listOf("install", "-q"),
            emptyList<String>(),
            null
        )
        val installTransformRunnable = TransformRunnable()
        runInEdt {
            try {
                transformMavenRunner.run(installParams, mvnSettings, installTransformRunnable)
            } catch (t: Throwable) {
                val error = "Maven Install: Unexpected error when executing bundled Maven install"
                installTransformRunnable.exitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
                buildlogBuilder.appendLine("IntelliJ bundled Maven install failed: ${t.message}")
                emitMavenFailure(error, t)
            }
        }
        return installTransformRunnable
    }

    fun runMavenCopyDependencies(sourceFolder: File, buildlogBuilder: StringBuilder, mvnSettings: MavenRunnerSettings, transformMavenRunner: TransformMavenRunner, destinationDir: Path): TransformRunnable {
        buildlogBuilder.appendLine("Command Run: IntelliJ bundled Maven dependency:copy-dependencies")
        val copyCommandList = listOf(
            "dependency:copy-dependencies",
            "-DoutputDirectory=$destinationDir",
            "-Dmdep.useRepositoryLayout=true",
            "-Dmdep.copyPom=true",
            "-Dmdep.addParentPoms=true",
            "-q",
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
                copyTransformRunnable.exitCode(Integer.MIN_VALUE) // to stop looking for the exitCode
                LOG.error(t) { error }
                buildlogBuilder.appendLine("IntelliJ bundled Maven copy dependencies failed: ${t.message}")
                CodetransformTelemetry.mvnBuildFailed(
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformMavenBuildCommand = CodeTransformMavenBuildCommand.IDEBundledMaven,
                    reason = error
                )
            }
        }
        return copyTransformRunnable
    }


    private fun identifyAvailableJdksForVersion(desired: JavaSdkVersion): List<Sdk> {
        val javaSdkType = ExternalSystemJdkProvider.getInstance().javaSdkType;
        val allJdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdkType)
        return allJdks.filter{JavaSdkImpl.getInstance().getVersion(it) == desired}
    }

    private fun resolveSuitableJreName(desired: JavaSdkVersion): String? {
        val jdk = getAvailableJdk(project) // current Jdk
        if(JavaSdkImpl.getInstance().getVersion(jdk.second) != desired){
            LOG.info("Chosen JDK is not compatible with configured JDK in project settings, looking for alternative JDK to use.")
            val compatibleJdks = identifyAvailableJdksForVersion(desired)
            if(compatibleJdks.isEmpty()){
                emitMavenFailure("No compatible jdk version matching $desired was found")
                return null // TODO show details like no matching JDK to $desired found, add a JDK matching java version $desired to project structure to continue
            } else {
                val compatibleJdk = compatibleJdks.first().name
                LOG.info("Found compatible jdk version matching $desired") // TODO maybe delete?
                return compatibleJdk
            }
        } else {
            return jdk.second.name
        }
    }

    /**
     * @description
     * this command is used to run the maven commmands which copies all the dependencies to a temp file which we will use to zip our own files to
     */
    fun runMavenCopyCommands(sourceFolder: File, buildlogBuilder: StringBuilder, desiredJavaSdkVersion: JavaSdkVersion): MavenCopyCommandsResult {
        val currentTimestamp = System.currentTimeMillis()
        val destinationDir = Files.createTempDirectory("transformation_dependencies_temp_" + currentTimestamp)

        LOG.info { "Executing IntelliJ bundled Maven" }
        try {
            // Create shared parameters
            val transformMvnRunner = TransformMavenRunner(project)
            val mvnSettings = MavenRunner.getInstance(project).settings.clone() // clone required to avoid editing user settings
            mvnSettings.setJreName(resolveSuitableJreName(desiredJavaSdkVersion) ?: return MavenCopyCommandsResult.Failure)

            // Run clean
            val cleanRunnable = runMavenClean(sourceFolder, buildlogBuilder, mvnSettings, transformMvnRunner )
            cleanRunnable.await()
            buildlogBuilder.appendLine(cleanRunnable.getOutput())
            if (cleanRunnable.isComplete() == 0) {
                val successMsg = "IntelliJ bundled Maven clean executed successfully"
                LOG.info { successMsg }
                buildlogBuilder.appendLine(successMsg)
            } else if (cleanRunnable.isComplete() != Integer.MIN_VALUE) {
                emitMavenFailure("Maven Clean: bundled Maven failed: exitCode ${cleanRunnable.isComplete()}")
                return MavenCopyCommandsResult.Failure
            }

            // Run install
            val installRunnable = runMavenInstall(sourceFolder, buildlogBuilder, mvnSettings, transformMvnRunner)
            installRunnable.await()
            buildlogBuilder.appendLine(installRunnable.getOutput())
            if (installRunnable.isComplete() == 0) {
                val successMsg = "IntelliJ bundled Maven install executed successfully"
                LOG.info { successMsg }
                buildlogBuilder.appendLine(successMsg)
            } else if (installRunnable.isComplete() != Integer.MIN_VALUE) {
                emitMavenFailure("Maven Install: bundled Maven failed: exitCode ${installRunnable.isComplete()}")
                return MavenCopyCommandsResult.Failure
            }

            // run copy dependencies
            val copyDependenciesRunnable = runMavenCopyDependencies(sourceFolder, buildlogBuilder,mvnSettings, transformMvnRunner, destinationDir)
            copyDependenciesRunnable.await()
            buildlogBuilder.appendLine(copyDependenciesRunnable.getOutput())
            if (copyDependenciesRunnable.isComplete() == 0) {
                val successMsg = "IntelliJ bundled Maven copy-dependencies executed successfully"
                LOG.info { successMsg }
                buildlogBuilder.appendLine(successMsg)
            } else {
                emitMavenFailure("Maven Copy: bundled Maven failed: exitCode ${copyDependenciesRunnable.isComplete()}")
                return MavenCopyCommandsResult.Failure
            }
        } catch (t: Throwable) {
            emitMavenFailure("IntelliJ bundled Maven executed failed: ${t.message}", t)
            return MavenCopyCommandsResult.Failure
        }
        // When all commands executed successfully, show the transformation hub
        return MavenCopyCommandsResult.Success(destinationDir.toFile())
    }

    companion object {
        private val LOG = getLogger<CodeModernizerSessionContext>()
    }
}
