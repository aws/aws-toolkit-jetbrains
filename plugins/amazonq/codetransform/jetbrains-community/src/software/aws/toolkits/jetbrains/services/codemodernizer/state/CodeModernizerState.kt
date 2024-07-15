// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.state

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.util.xmlb.annotations.Property
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.toVirtualFile
import java.time.Instant

enum class JobDetails {
    LAST_JOB_ID,
    CONFIGURATION_FILE_PATH,
    TARGET_JAVA_VERSION,
    SOURCE_JAVA_VERSION,
    QCT_START_TIME,
}

enum class StateFlags {
    IS_ONGOING
}

fun buildState(context: CodeModernizerSessionContext, isJobOngoing: Boolean, jobId: JobId, startTime: Instant) = CodeModernizerState().apply {
    lastJobContext.putAll(
        setOf(
            JobDetails.LAST_JOB_ID to jobId.id,
            JobDetails.CONFIGURATION_FILE_PATH to context.configurationFile.path,
            JobDetails.TARGET_JAVA_VERSION to context.targetJavaVersion.description,
            JobDetails.SOURCE_JAVA_VERSION to context.sourceJavaVersion.description,
            JobDetails.QCT_START_TIME to startTime.toEpochMilli().toString()
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

    fun isJobOngoing() = flags.getOrDefault(StateFlags.IS_ONGOING, false)
    fun getLatestJobId() = JobId(lastJobContext[JobDetails.LAST_JOB_ID] ?: throw RuntimeException("No Job has been executed!"))

    /**
     * Return latest job start time or the [Instant.EPOCH] if last job did not have a `registered` start time in state.
     */
    fun getLatestJobStartTime(): Instant {
        val defaultValue = ""
        val lastStartTime = lastJobContext.getOrDefault(JobDetails.QCT_START_TIME, defaultValue)
        if (lastStartTime == defaultValue) return Instant.EPOCH
        return Instant.ofEpochMilli(lastStartTime.toLong())
    }

    fun toSessionContext(project: Project): CodeModernizerSessionContext {
        val configurationFile = lastJobContext[JobDetails.CONFIGURATION_FILE_PATH]?.toVirtualFile()
            ?: throw RuntimeException("No build file store in the state")
        val targetString =
            lastJobContext[JobDetails.TARGET_JAVA_VERSION] ?: throw RuntimeException("Expected target language for migration path of previous job but was null")
        val sourceString =
            lastJobContext[JobDetails.SOURCE_JAVA_VERSION] ?: throw RuntimeException("Expected source language for migration path of previous job but was null")
        val targetJavaSdkVersion = JavaSdkVersion.fromVersionString(targetString) ?: throw RuntimeException("Invalid Java SDK version $targetString")
        val sourceJavaSdkVersion = JavaSdkVersion.fromVersionString(sourceString) ?: throw RuntimeException("Invalid Java SDK version $sourceString")
        return CodeModernizerSessionContext(project, configurationFile, sourceJavaSdkVersion, targetJavaSdkVersion)
    }
}
