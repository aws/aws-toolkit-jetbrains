// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.state

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.util.xmlb.annotations.Property
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_BUILD_RUN_UNIT_TESTS
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.toVirtualFile

enum class JobDetails {
    LAST_JOB_ID,
    CONFIGURATION_FILE_PATH,
    TARGET_JAVA_VERSION,
    SOURCE_JAVA_VERSION,
    CUSTOM_BUILD_COMMAND,
}

enum class StateFlags {
    IS_ONGOING,
}

fun buildState(context: CodeModernizerSessionContext, isJobOngoing: Boolean, jobId: JobId) = CodeModernizerState().apply {
    lastJobContext.putAll(
        setOf(
            JobDetails.LAST_JOB_ID to jobId.id,
            JobDetails.CONFIGURATION_FILE_PATH to if (context.configurationFile != null) context.configurationFile!!.path else "",
            JobDetails.TARGET_JAVA_VERSION to context.targetJavaVersion.description,
            JobDetails.SOURCE_JAVA_VERSION to context.sourceJavaVersion.description,
            JobDetails.CUSTOM_BUILD_COMMAND to context.customBuildCommand
        )
    )
    flags.putAll(
        setOf(
            StateFlags.IS_ONGOING to isJobOngoing
        )
    )
}

class CodeModernizerState : BaseState() {
    @get:Property
    val lastJobContext by map<JobDetails, String>()

    @get:Property
    val flags by map<StateFlags, Boolean>()

    fun getLatestJobId() = JobId(lastJobContext[JobDetails.LAST_JOB_ID] ?: throw RuntimeException("No Job has been executed!"))

    fun toSessionContext(project: Project): CodeModernizerSessionContext {
        val configurationFile = lastJobContext[JobDetails.CONFIGURATION_FILE_PATH]?.toVirtualFile()
            ?: throw RuntimeException("No build file store in the state")
        val targetString =
            lastJobContext[JobDetails.TARGET_JAVA_VERSION] ?: throw RuntimeException("Expected target language for migration path of previous job but was null")
        val sourceString =
            lastJobContext[JobDetails.SOURCE_JAVA_VERSION] ?: throw RuntimeException("Expected source language for migration path of previous job but was null")
        val targetJavaSdkVersion = JavaSdkVersion.fromVersionString(targetString) ?: throw RuntimeException("Invalid Java SDK version $targetString")
        val sourceJavaSdkVersion = JavaSdkVersion.fromVersionString(sourceString) ?: throw RuntimeException("Invalid Java SDK version $sourceString")
        return CodeModernizerSessionContext(
            project,
            configurationFile,
            sourceJavaSdkVersion,
            targetJavaSdkVersion,
            lastJobContext[JobDetails.CUSTOM_BUILD_COMMAND] ?: MAVEN_BUILD_RUN_UNIT_TESTS // default to running unit tests
        )
    }
}
