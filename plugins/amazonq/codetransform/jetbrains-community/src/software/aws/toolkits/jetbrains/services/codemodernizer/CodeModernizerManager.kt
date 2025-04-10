// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.notification.NotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationJob
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_PROJECT_SIZE
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformMessageListener
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_POM_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.file.PomFileAnnotator
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerException
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerStartJobResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformHilDownloadArtifact
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformType
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CustomerSelection
import software.aws.toolkits.jetbrains.services.codemodernizer.model.Dependency
import software.aws.toolkits.jetbrains.services.codemodernizer.model.InvalidTelemetryReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_CONFIGURATION_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenDependencyReportCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.UploadFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ValidationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.panels.managers.CodeModernizerBottomWindowPanelManager
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeModernizerSessionState
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeModernizerState
import software.aws.toolkits.jetbrains.services.codemodernizer.state.StateFlags
import software.aws.toolkits.jetbrains.services.codemodernizer.state.buildState
import software.aws.toolkits.jetbrains.services.codemodernizer.toolwindow.CodeModernizerBottomToolWindowFactory
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.STATES_WHERE_PLAN_EXIST
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.createFileCopy
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.findLineNumberByString
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getJavaModulesWithSQL
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getMavenVersion
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getModuleOrProjectNameForFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getPathToHilArtifactPomFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getPathToHilDependencyReport
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getPathToHilDependencyReportDir
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getSupportedBuildFilesWithSupportedJdk
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getSupportedJavaMappings
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getSupportedModules
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isGradleProject
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.openTroubleshootingGuideNotificationAction
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.parseBuildFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.parseXmlDependenciesReport
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.setDependencyVersionInPom
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.tryGetJdk
import software.aws.toolkits.jetbrains.ui.feedback.CodeTransformFeedbackDialog
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.jetbrains.utils.notifyStickyError
import software.aws.toolkits.jetbrains.utils.notifyStickyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeTransformBuildSystem
import software.aws.toolkits.telemetry.CodeTransformCancelSrcComponents
import software.aws.toolkits.telemetry.CodeTransformPreValidationError
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.pathString

@State(name = "codemodernizerStates", storages = [Storage("aws.xml", roamingType = RoamingType.PER_OS)])
class CodeModernizerManager(private val project: Project) : PersistentStateComponent<CodeModernizerState>, Disposable {
    private val telemetry = CodeTransformTelemetryManager.getInstance(project)
    private var managerState = CodeModernizerState()
    val codeModernizerBottomWindowPanelManager by lazy { CodeModernizerBottomWindowPanelManager(project) }
    private val codeModernizerBottomWindowPanelContent by lazy {
        val contentManager = getBottomToolWindow().contentManager
        contentManager.removeAllContents(true)
        contentManager.factory.createContent(
            codeModernizerBottomWindowPanelManager,
            message("codemodernizer.toolwindow.scan_display"),
            false,
        ).also {
            Disposer.register(contentManager, it)
        }
    }
    private val supportedBuildFileNames = listOf(MAVEN_CONFIGURATION_FILE_NAME)
    private val isModernizationInProgress = AtomicBoolean(false)
    private val isResumingJob = AtomicBoolean(false)
    private val isMvnRunning = AtomicBoolean(false)
    private val isJobSuccessfullyResumed = AtomicBoolean(false)

    private val transformationStoppedByUsr = AtomicBoolean(false)
    var codeTransformationSession: CodeModernizerSession? = null
        set(session) {
            if (session != null) {
                Disposer.register(this, session)
            }
            field = session
        }
    private val artifactHandler = ArtifactHandler(project, GumbyClient.getInstance(project))
    private val supportedJavaMappings = mapOf(
        JavaSdkVersion.JDK_1_8 to setOf(JavaSdkVersion.JDK_17, JavaSdkVersion.JDK_21),
        JavaSdkVersion.JDK_11 to setOf(JavaSdkVersion.JDK_17, JavaSdkVersion.JDK_21),
        JavaSdkVersion.JDK_17 to setOf(JavaSdkVersion.JDK_17, JavaSdkVersion.JDK_21),
        JavaSdkVersion.JDK_21 to setOf(JavaSdkVersion.JDK_21),
    )

    init {
        CodeModernizerSessionState.getInstance(project).setDefaults()
        initQRegionProfileSelectedListener()
    }

    private fun initQRegionProfileSelectedListener() {
        project.messageBus.connect(this).subscribe(
            QRegionProfileSelectedListener.TOPIC,
            object : QRegionProfileSelectedListener {
                override fun onProfileSelected(project: Project, profile: QRegionProfile?) {
                    stopModernize()
                    codeTransformationSession?.let {
                        Disposer.dispose(it)
                    }
                    managerState = CodeModernizerState()
                    codeTransformationSession = null
                }
            }
        )
    }

    fun validate(project: Project, transformationType: CodeTransformType): ValidationResult {
        fun validateCore(project: Project): ValidationResult {
            if (isRunningOnRemoteBackend()) {
                return ValidationResult(
                    false,
                    InvalidTelemetryReason(
                        CodeTransformPreValidationError.RemoteRunProject,
                    )
                )
            }
            if (!isCodeTransformAvailable(project)) {
                return ValidationResult(
                    false,
                    InvalidTelemetryReason(
                        CodeTransformPreValidationError.NonSsoLogin,
                    )
                )
            }

            if (ProjectRootManager.getInstance(project).contentRoots.isEmpty()) {
                return ValidationResult(
                    false,
                    InvalidTelemetryReason(
                        CodeTransformPreValidationError.EmptyProject,
                    )
                )
            }

            if (transformationType == CodeTransformType.SQL_CONVERSION) {
                val javaModules = project.getJavaModulesWithSQL()
                return if (javaModules.isNotEmpty()) {
                    ValidationResult(
                        true,
                        metadata = "found ${javaModules.size} modules with SQL"
                    )
                } else {
                    ValidationResult(
                        false,
                        InvalidTelemetryReason(
                            CodeTransformPreValidationError.NoJavaProject,
                        )
                    )
                }
            }

            val supportedModules = project.getSupportedModules(supportedJavaMappings).toSet()
            val validProjectJdk = project.getSupportedJavaMappings(supportedJavaMappings).isNotEmpty()
            val projectJdk = project.tryGetJdk()
            if (supportedModules.isEmpty() && !validProjectJdk) {
                return ValidationResult(
                    false,
                    InvalidTelemetryReason(
                        CodeTransformPreValidationError.UnsupportedJavaVersion,
                        projectJdk.toString()
                    )
                )
            }
            val validatedBuildFiles = project.getSupportedBuildFilesWithSupportedJdk(supportedBuildFileNames, supportedJavaMappings)
            return if (validatedBuildFiles.isNotEmpty()) {
                ValidationResult(
                    true,
                    validatedBuildFiles = validatedBuildFiles,
                    buildSystem = CodeTransformBuildSystem.Maven,
                    buildSystemVersion = getMavenVersion(project)
                )
            } else {
                ValidationResult(
                    false,
                    invalidTelemetryReason = InvalidTelemetryReason(
                        CodeTransformPreValidationError.UnsupportedBuildSystem,
                        if (isGradleProject(project)) "Gradle build" else "other build"
                    ),
                    buildSystem = if (isGradleProject(project)) CodeTransformBuildSystem.Gradle else CodeTransformBuildSystem.Unknown
                )
            }
        }

        val result = validateCore(project)

        telemetry.validateProject(result)

        return result
    }

    /**
     * The initial landing UI for the results view panel.
     * This method adds code content to the problems view if not already added.
     * When [setSelected] is true, code scan panel is set to be in focus.
     */
    fun addCodeModernizeUI(setSelected: Boolean = false, moduleOrProjectNameForFile: String? = null) = runInEdt {
        val appModernizerBottomWindow = getBottomToolWindow()
        appModernizerBottomWindow.isAvailable = true
        if (!appModernizerBottomWindow.contentManager.contents.contains(codeModernizerBottomWindowPanelContent)) {
            appModernizerBottomWindow.contentManager.addContent(codeModernizerBottomWindowPanelContent)
        }
        codeModernizerBottomWindowPanelContent.displayName = message("codemodernizer.toolwindow.scan_display")
        if (moduleOrProjectNameForFile == null) {
            appModernizerBottomWindow.stripeTitle = message("codemodernizer.toolwindow.label_no_job")
        } else {
            appModernizerBottomWindow.stripeTitle = message("codemodernizer.toolwindow.label", moduleOrProjectNameForFile)
        }

        if (setSelected) {
            appModernizerBottomWindow.contentManager.setSelectedContent(codeModernizerBottomWindowPanelContent)
            appModernizerBottomWindow.show()
        }
    }

    /**
     * @description Main function for triggering the start of elastic gumby migration.
     */
    fun initModernizationJobUI(shouldOpenBottomWindowOnStart: Boolean = true, moduleOrProjectNameForFile: String) {
        isModernizationInProgress.set(true)
        // Initialize the bottom toolkit window with content
        addCodeModernizeUI(shouldOpenBottomWindowOnStart, moduleOrProjectNameForFile)
        codeModernizerBottomWindowPanelManager.setJobStartingUI()
    }

    fun stopModernize() {
        if (isModernizationJobActive()) {
            userInitiatedStopCodeModernization()
            telemetry.jobIsCancelledByUser(CodeTransformCancelSrcComponents.DevToolsStopButton)
        }
    }

    fun runModernize(copyResult: MavenCopyCommandsResult? = null) {
        initStopParameters()
        val session = codeTransformationSession ?: return
        initModernizationJobUI(true, project.getModuleOrProjectNameForFile(session.sessionContext.configurationFile))
        launchModernizationJob(session, copyResult)
    }

    suspend fun resumePollingFromHil() {
        val transformationType =
            if (codeTransformationSession?.sessionContext?.sqlMetadataZip != null) CodeTransformType.SQL_CONVERSION else CodeTransformType.LANGUAGE_UPGRADE
        val result = handleJobResumedFromHil(managerState.getLatestJobId(), codeTransformationSession as CodeModernizerSession, transformationType)
        postModernizationJob(result)
    }

    private fun initStopParameters() {
        transformationStoppedByUsr.set(false)
        CodeModernizerSessionState.getInstance(project).currentJobStatus = TransformationStatus.UNKNOWN_TO_SDK_VERSION
        CodeModernizerSessionState.getInstance(project).currentJobCreationTime = Instant.MIN
        CodeModernizerSessionState.getInstance(project).currentJobStopTime = Instant.MIN
    }

    private fun notifyJobFailure(failureReason: String?, actions: Collection<AnAction> = listOf()) {
        val reason = failureReason ?: message("codemodernizer.notification.info.modernize_failed.unknown_failure_reason") // should not happen
        notifyStickyInfo(
            message("codemodernizer.notification.info.modernize_failed.title"),
            reason,
            project,
            listOf(displayFeedbackNotificationAction(), *actions.toTypedArray())
        )
    }

    internal fun notifyTransformationStopped() {
        notifyStickyInfo(
            message("codemodernizer.notification.info.transformation_stop.title"),
            message("codemodernizer.notification.info.transformation_stop.content"),
            project,
            listOf(displayFeedbackNotificationAction())
        )
    }

    internal fun notifyUnableToResumeJob() {
        notifyStickyInfo(
            message("codemodernizer.notification.info.transformation_resume.title"),
            message("codemodernizer.notification.info.transformation_resume.content"),
            project,
            listOf(displayFeedbackNotificationAction())
        )
    }

    internal fun notifyTransformationStartStopping() {
        notifyStickyInfo(
            message("codemodernizer.notification.info.transformation_start_stopping.title"),
            message("codemodernizer.notification.info.transformation_start_stopping.content"),
            project,
        )
    }

    internal fun notifyTransformationFailedToStop() {
        notifyStickyError(
            message("codemodernizer.notification.info.transformation_start_stopping.failed_title"),
            message("codemodernizer.notification.info.transformation_start_stopping.failed_content"),
            project,
            listOf(displayFeedbackNotificationAction())
        )
    }

    fun launchModernizationJob(session: CodeModernizerSession, copyResult: MavenCopyCommandsResult?) = projectCoroutineScope(project).launch {
        val result = initModernizationJob(session, copyResult)

        postModernizationJob(result)
    }

    fun resumeJob(session: CodeModernizerSession, lastJobId: JobId, currentJobResult: TransformationJob) = projectCoroutineScope(project).launch {
        isJobSuccessfullyResumed.set(true)
        CodeTransformMessageListener.instance.onTransformResuming()
        if (isModernizationJobActive()) {
            runInEdt { getBottomToolWindow().show() }
            return@launch
        }
        try {
            val plan = if (currentJobResult.status() in STATES_WHERE_PLAN_EXIST) {
                try {
                    delay(1000)
                    session.fetchPlan(lastJobId).transformationPlan()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            CodeModernizerSessionState.getInstance(project).currentJobCreationTime = currentJobResult.creationTime()
            codeTransformationSession = session
            initModernizationJobUI(false, project.getModuleOrProjectNameForFile(session.sessionContext.configurationFile))
            val transformationType = if (session.sessionContext.sqlMetadataZip != null) CodeTransformType.SQL_CONVERSION else CodeTransformType.LANGUAGE_UPGRADE
            codeModernizerBottomWindowPanelManager.setResumeJobUI(currentJobResult, plan, session.sessionContext.sourceJavaVersion, transformationType)
            session.resumeJob(currentJobResult.creationTime(), lastJobId)
            val result = handleJobStarted(lastJobId, session)
            postModernizationJob(result)
        } catch (e: Exception) {
            notifyUnableToResumeJob()
            LOG.error(e) { e.message.toString() }
            return@launch
        }
    }

    fun setJobOngoing(jobId: JobId, sessionContext: CodeModernizerSessionContext) {
        isModernizationInProgress.set(true)
        managerState = buildState(sessionContext, true, jobId)
    }

    fun setJobNotOngoing() {
        isModernizationInProgress.set(false)
        managerState.flags[StateFlags.IS_ONGOING] = false
    }

    fun isJobOngoingInState() = managerState.flags.getOrDefault(StateFlags.IS_ONGOING, false)

    fun handleLocalMavenBuildResult(mavenCopyCommandsResult: MavenCopyCommandsResult) {
        codeTransformationSession?.setLastMvnBuildResult(mavenCopyCommandsResult)
        // Send IDE notifications first
        if (mavenCopyCommandsResult is MavenCopyCommandsResult.Failure) {
            notifyStickyInfo(
                message("codemodernizer.notification.warn.maven_failed.title"),
                message("codemodernizer.notification.warn.maven_failed.content"),
                project,
                listOf(openTroubleshootingGuideNotificationAction(CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE), displayFeedbackNotificationAction()),
            )
        } else if (mavenCopyCommandsResult is MavenCopyCommandsResult.NoJdk) {
            notifyStickyInfo(
                message("codemodernizer.notification.warn.maven_failed.title"),
                message("codemodernizer.notification.warn.validation.no_jdk"),
                project,
                listOf(openTroubleshootingGuideNotificationAction(CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE), displayFeedbackNotificationAction()),
            )
        }

        CodeTransformMessageListener.instance.onMavenBuildResult(mavenCopyCommandsResult)
    }

    fun runLocalMavenBuild(project: Project, session: CodeModernizerSession) {
        projectCoroutineScope(project).launch {
            isMvnRunning.set(true)
            val result = session.getDependenciesUsingMaven()
            isMvnRunning.set(false)
            handleLocalMavenBuildResult(result)
        }
    }

    fun parseBuildFile(): String? = parseBuildFile(codeTransformationSession?.sessionContext?.configurationFile)

    private suspend fun initModernizationJob(session: CodeModernizerSession, copyResult: MavenCopyCommandsResult?): CodeModernizerJobCompletedResult =
        when (val result = session.createModernizationJob(copyResult)) {
            is CodeModernizerStartJobResult.ZipCreationFailed -> {
                CodeModernizerJobCompletedResult.UnableToCreateJob(
                    message("codemodernizer.notification.warn.zip_creation_failed", result.reason),
                    false,
                )
            }

            is CodeModernizerStartJobResult.UnableToStartJob -> {
                CodeModernizerJobCompletedResult.UnableToCreateJob(
                    message("codemodernizer.notification.warn.unable_to_start_job", result.exception),
                    true,
                )
            }

            is CodeModernizerStartJobResult.ZipUploadFailed -> {
                CodeModernizerJobCompletedResult.ZipUploadFailed(
                    result.reason
                )
            }

            is CodeModernizerStartJobResult.Started -> {
                handleJobStarted(result.jobId, session)
            }

            is CodeModernizerStartJobResult.Disposed -> {
                CodeModernizerJobCompletedResult.ManagerDisposed
            }

            is CodeModernizerStartJobResult.Cancelled -> {
                CodeModernizerJobCompletedResult.JobAbortedBeforeStarting
            }

            is CodeModernizerStartJobResult.CancelledMissingDependencies -> {
                CodeModernizerJobCompletedResult.JobAbortedMissingDependencies
            }

            is CodeModernizerStartJobResult.CancelledZipTooLarge -> {
                CodeModernizerJobCompletedResult.JobAbortedZipTooLarge
            }
        }

    private suspend fun handleJobResumedFromHil(
        jobId: JobId,
        session: CodeModernizerSession,
        transformType: CodeTransformType,
    ): CodeModernizerJobCompletedResult = session.pollUntilJobCompletion(
        transformType,
        jobId
    ) { new, plan ->
        codeModernizerBottomWindowPanelManager.handleJobTransition(new, plan, session.sessionContext.sourceJavaVersion, transformType)
    }

    private suspend fun handleJobStarted(jobId: JobId, session: CodeModernizerSession): CodeModernizerJobCompletedResult {
        setJobOngoing(jobId, session.sessionContext)
        // Init the splitter panel to show progress and progress steps
        // https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html#write-access
        ApplicationManager.getApplication().invokeLater {
            codeModernizerBottomWindowPanelManager.setJobRunningUI()
        }

        val transformType = if (session.sessionContext.sqlMetadataZip != null) CodeTransformType.SQL_CONVERSION else CodeTransformType.LANGUAGE_UPGRADE

        return session.pollUntilJobCompletion(transformType, jobId) { new, plan ->
            codeModernizerBottomWindowPanelManager.handleJobTransition(new, plan, session.sessionContext.sourceJavaVersion, transformType)
        }
    }

    private fun postModernizationJob(result: CodeModernizerJobCompletedResult) {
        codeTransformationSession?.setLastTransformResult(result)

        if (result is CodeModernizerJobCompletedResult.ManagerDisposed) {
            return
        }

        if (result is CodeModernizerJobCompletedResult.JobPaused) {
            codeTransformationSession?.setHilDownloadArtifactId(result.downloadArtifactId)

            CodeTransformMessageListener.instance.onStartingHil()

            return
        }

        // https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html#write-access
        ApplicationManager.getApplication().invokeLater {
            setJobNotOngoing()
            if (!transformationStoppedByUsr.get()) {
                informUserOfCompletion(result)
                codeModernizerBottomWindowPanelManager.setJobFinishedUI(result)
                CodeTransformMessageListener.instance.onTransformResult(result)
            } else {
                codeModernizerBottomWindowPanelManager.userInitiatedStopCodeModernizationUI()
                notifyTransformationStopped()
                transformationStoppedByUsr.set(false)
                CodeTransformMessageListener.instance.onTransformStopped()
            }
        }
    }

    /**
     * Silently try to resume the job, informs users only when job successfully resumed, suppresses exceptions.
     */
    fun tryResumeJob() = projectCoroutineScope(project).launch {
        try {
            val notYetResumed = isResumingJob.compareAndSet(false, true)
            // If the job is already running, compareAndSet will return false because the expected
            // behavior is that the job is not running when trying to resume
            if (!notYetResumed) {
                return@launch
            }

            LOG.info { "Attempting to resume job, current state is: $managerState" }
            if (!managerState.flags.getOrDefault(StateFlags.IS_ONGOING, false)) return@launch

            val context = managerState.toSessionContext(project)
            val session = CodeModernizerSession(context)
            val lastJobId = managerState.getLatestJobId()
            LOG.info { "Attempting to resume job with id $lastJobId" }
            val result = session.getJobDetails(lastJobId)
            when (result.status()) {
                TransformationStatus.COMPLETED -> {
                    resumeJob(session, lastJobId, result)
                    setJobNotOngoing()
                }

                TransformationStatus.PARTIALLY_COMPLETED -> {
                    resumeJob(session, lastJobId, result)
                    setJobNotOngoing()
                }

                TransformationStatus.UNKNOWN_TO_SDK_VERSION -> {
                    notifyStickyInfo(
                        message("codemodernizer.notification.warn.on_resume.unknown_status_response.title"),
                        message("codemodernizer.notification.warn.on_resume.unknown_status_response.content"),
                    )
                    setJobNotOngoing()
                }

                TransformationStatus.STOPPED, TransformationStatus.STOPPING -> {
                    // If user stopped the last job, there is no need for us to resume the job
                    setJobNotOngoing()
                }

                else -> {
                    resumeJob(session, lastJobId, result)
                    notifyStickyInfo(
                        message("codemodernizer.manager.job_ongoing_title"),
                        message("codemodernizer.manager.job_ongoing_content"),
                        project,
                        listOf(resumeJobNotificationAction(session, lastJobId, result)),
                    )
                }
            }
            telemetry.jobIsResumedAfterIdeClose(lastJobId, result.status())
        } catch (e: AccessDeniedException) {
            LOG.error { "Unable to resume job as credentials are invalid" }
            // User is logged in with old or invalid credentials, nothing to do until they log in with valid credentials
        } catch (e: Exception) {
            LOG.error(e) { "Unable to resume job as an unexpected exception occurred" }
        } finally {
            isResumingJob.set(false)
        }
    }

    private fun resumeJobNotificationAction(session: CodeModernizerSession, lastJobId: JobId, currentJobResult: TransformationJob) =
        NotificationAction.createSimple(message("codemodernizer.notification.info.modernize_ongoing.view_status")) {
            resumeJob(session, lastJobId, currentJobResult)
        }

    private fun displaySummaryNotificationAction(jobId: JobId) =
        NotificationAction.createSimple(message("codemodernizer.notification.info.modernize_complete.view_summary")) {
            artifactHandler.showTransformationSummary(jobId)
        }

    private fun displayFeedbackNotificationAction() =
        NotificationAction.createSimple(message("codemodernizer.notification.warn.submit_feedback")) {
            CodeTransformFeedbackDialog(project).showAndGet()
        }

    private fun informUserOfCompletion(result: CodeModernizerJobCompletedResult) {
        var jobId: JobId? = null
        when (result) {
            is CodeModernizerJobCompletedResult.UnableToCreateJob -> notifyJobFailure(
                result.failureReason
            )

            is CodeModernizerJobCompletedResult.ZipUploadFailed -> {
                if (result.failureReason is UploadFailureReason.CREDENTIALS_EXPIRED) {
                    setJobNotOngoing()
                    CodeTransformMessageListener.instance.onCheckAuth()
                    notifyJobFailure(
                        message("codemodernizer.notification.warn.upload_failed_expired_credentials.content"),
                        listOf(
                            NotificationAction.createSimpleExpiring(message("codemodernizer.notification.warn.action.reauthenticate")) {
                                CodeTransformMessageListener.instance.onReauthStarted()
                            }
                        )
                    )
                } else {
                    notifyJobFailure(
                        message("codemodernizer.notification.warn.upload_failed", result.failureReason.toString()),
                    )
                }
            }

            is CodeModernizerJobCompletedResult.RetryableFailure -> notifyJobFailure(
                result.failureReason
            )

            is CodeModernizerJobCompletedResult.JobFailed -> notifyJobFailure(
                result.failureReason
            )

            is CodeModernizerJobCompletedResult.JobFailedInitialBuild -> notifyStickyInfo(
                message("codemodernizer.builderrordialog.description.title"),
                result.failureReason,
                project,
                listOf(displayFeedbackNotificationAction())
            )

            is CodeModernizerJobCompletedResult.JobPartiallySucceeded -> {
                notifyStickyInfo(
                    message("codemodernizer.notification.info.modernize_partial_complete.title"),
                    message("codemodernizer.notification.info.modernize_partial_complete.content"),
                    project,
                    listOf(displaySummaryNotificationAction(result.jobId), displayFeedbackNotificationAction()),
                )
                jobId = result.jobId
            }

            is CodeModernizerJobCompletedResult.JobCompletedSuccessfully -> {
                notifyStickyInfo(
                    message("codemodernizer.notification.info.modernize_complete.title"),
                    message("codemodernizer.notification.info.modernize_complete.content"),
                    project,
                    listOf(displaySummaryNotificationAction(result.jobId)),
                )
                jobId = result.jobId
            }

            is CodeModernizerJobCompletedResult.ManagerDisposed -> LOG.warn { "Manager disposed" }
            is CodeModernizerJobCompletedResult.JobAbortedBeforeStarting -> LOG.warn { "Job was aborted" }
            is CodeModernizerJobCompletedResult.JobAbortedMissingDependencies -> notifyStickyInfo(
                message("codemodernizer.notification.warn.maven_failed.title"),
                message("codemodernizer.notification.warn.maven_failed.content"),
                project,
                listOf(openTroubleshootingGuideNotificationAction(CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE), displayFeedbackNotificationAction()),
            )
            is CodeModernizerJobCompletedResult.JobAbortedZipTooLarge -> notifyStickyInfo(
                message("codemodernizer.notification.warn.zip_too_large.title"),
                message("codemodernizer.notification.warn.zip_too_large.content"),
                project,
                listOf(openTroubleshootingGuideNotificationAction(CODE_TRANSFORM_TROUBLESHOOT_DOC_PROJECT_SIZE), displayFeedbackNotificationAction()),
            )
            is CodeModernizerJobCompletedResult.Stopped -> notifyStickyInfo(
                message("codemodernizer.notification.info.transformation_stop.title"),
                message("codemodernizer.notification.info.transformation_stop.content"),
                project,
                listOf(displayFeedbackNotificationAction())
            )

            is CodeModernizerJobCompletedResult.JobPaused -> return
        }
        telemetry.totalRunTime(result.toString(), jobId)
    }

    fun createCodeModernizerSession(customerSelection: CustomerSelection, project: Project) {
        codeTransformationSession = CodeModernizerSession(
            CodeModernizerSessionContext(
                project = project,
                configurationFile = customerSelection.configurationFile,
                sourceJavaVersion = customerSelection.sourceJavaVersion,
                targetJavaVersion = customerSelection.targetJavaVersion,
                sourceVendor = customerSelection.sourceVendor,
                targetVendor = customerSelection.targetVendor,
                sourceServerName = customerSelection.sourceServerName,
                sqlMetadataZip = customerSelection.sqlMetadataZip,
            ),
        )
    }

    fun showModernizationProgressUI() = codeModernizerBottomWindowPanelManager.showUnalteredJobUI()

    fun showPreviousJobHistoryUI() {
        codeModernizerBottomWindowPanelManager.setPreviousJobHistoryUI(isModernizationJobActive())
    }

    fun userInitiatedStopCodeModernization() {
        notifyTransformationStartStopping()
        codeTransformationSession?.hilCleanup()
        if (transformationStoppedByUsr.getAndSet(true)) return
        val currentId = codeTransformationSession?.getActiveJobId()
        projectCoroutineScope(project).launch {
            try {
                val success = codeTransformationSession?.stopTransformation(currentId?.id) ?: true // no session -> no job to stop
                if (!success) {
                    // This should not happen
                    throw CodeModernizerException(message("codemodernizer.notification.info.transformation_start_stopping.as_no_response"))
                }
            } catch (e: Exception) {
                LOG.error(e) { e.message.toString() }
                notifyTransformationFailedToStop()
            } finally {
                telemetry.totalRunTime("JobCancelled", currentId)
            }
        }
    }

    fun isModernizationJobResuming(): Boolean = isResumingJob.get()

    fun isModernizationJobStopping(): Boolean = transformationStoppedByUsr.get()

    fun isModernizationJobActive(): Boolean = isModernizationInProgress.get()

    fun isRunningMvn(): Boolean = isMvnRunning.get()

    fun isJobSuccessfullyResumed(): Boolean = isJobSuccessfullyResumed.get()

    fun getLastMvnBuildResult(): MavenCopyCommandsResult? = codeTransformationSession?.getLastMvnBuildResult()

    fun getLastTransformResult(): CodeModernizerJobCompletedResult? = codeTransformationSession?.getLastTransformResult()

    fun getCurrentHilArtifact(): CodeTransformHilDownloadArtifact? = codeTransformationSession?.getHilDownloadArtifact()

    fun getBottomToolWindow() = ToolWindowManager.getInstance(project).getToolWindow(CodeModernizerBottomToolWindowFactory.id)
        ?: error(message("codemodernizer.toolwindow.problems_window_not_found"))

    fun getMvnBuildWindow() = ToolWindowManager.getInstance(project).getToolWindow("Run")
        ?: error(message("codemodernizer.toolwindow.problems_mvn_window_not_found"))

    override fun getState(): CodeModernizerState = CodeModernizerState().apply {
        lastJobContext.putAll(managerState.lastJobContext)
        flags.putAll(managerState.flags)
    }

    override fun loadState(state: CodeModernizerState) {
        managerState.lastJobContext.clear()
        managerState.lastJobContext.putAll(state.lastJobContext)
        managerState.flags.clear()
        managerState.flags.putAll(state.flags)
    }

    fun getTransformationPlan(): TransformationPlan? = codeTransformationSession?.getTransformationPlan()
    fun getTransformationSummary(): TransformationSummary? {
        val job = codeTransformationSession?.getActiveJobId() ?: return null
        return artifactHandler.getSummary(job)
    }

    companion object {
        fun getInstance(project: Project): CodeModernizerManager = project.service()
        val LOG = getLogger<CodeModernizerManager>()
    }

    override fun dispose() {}
    fun showTransformationSummary() {
        val job = codeTransformationSession?.getActiveJobId() ?: return
        artifactHandler.showTransformationSummary(job)
    }

    fun showTransformationPlan() {
        codeTransformationSession?.tryOpenTransformationPlanEditor()
    }

    fun handleCredentialsChanged() {
        codeTransformationSession?.dispose()
        codeModernizerBottomWindowPanelManager.reset()
        isModernizationInProgress.set(false)
    }

    suspend fun getArtifactForHil(): CodeTransformHilDownloadArtifact? {
        if (codeTransformationSession?.getHilDownloadArtifact() != null) {
            return codeTransformationSession?.getHilDownloadArtifact()
        }

        val jobId = codeTransformationSession?.getActiveJobId() ?: return null
        val downloadArtifactId = codeTransformationSession?.getHilDownloadArtifactId() ?: return null

        val tmpDir = try {
            createTempDirectory("", null)
        } catch (e: Exception) {
            val errorMessage = "Unexpected error when creating tmp dir for HIL: ${e.localizedMessage}"
            LOG.error { errorMessage }
            return null
        }
        try {
            val hilArtifact = artifactHandler.downloadHilArtifact(jobId, downloadArtifactId, tmpDir)
            if (hilArtifact != null) {
                codeTransformationSession?.setHilTempDirectoryPath(tmpDir.toPath())
                codeTransformationSession?.setHilDownloadArtifact(hilArtifact)
            }
            return hilArtifact
        } catch (e: Exception) {
            return null
        }
    }

    fun createDependencyReport(hilDownloadArtifact: CodeTransformHilDownloadArtifact): MavenDependencyReportCommandsResult {
        val tmpDirPath = codeTransformationSession?.getHilTempDirectoryPath() ?: return MavenDependencyReportCommandsResult.Failure

        try {
            val copyPomForDependencyReport = createFileCopy(hilDownloadArtifact.pomFile, getPathToHilDependencyReportDir(tmpDirPath).resolve(HIL_POM_FILE_NAME))
            setDependencyVersionInPom(copyPomForDependencyReport, hilDownloadArtifact.manifest.sourcePomVersion)
        } catch (e: Exception) {
            val errorMessage = "Unexpected error when preparing for HIL dependency report: ${e.localizedMessage}"
            telemetry.error(errorMessage)
            LOG.error { errorMessage }
            return MavenDependencyReportCommandsResult.Failure
        }

        return codeTransformationSession?.createHilDependencyReportUsingMaven() as MavenDependencyReportCommandsResult
    }

    fun findAvailableVersionForDependency(pomGroupId: String, pomArtifactId: String): Dependency? {
        try {
            val hilTempDirPath = codeTransformationSession?.getHilTempDirectoryPath()
                ?: throw CodeModernizerException("Cannot get HIL temp directory")

            val report = parseXmlDependenciesReport(
                getPathToHilDependencyReport(hilTempDirPath)
            )
            return report.dependencies?.first {
                it.groupId == pomGroupId &&
                    it.artifactId == pomArtifactId
            }
        } catch (e: NoSuchElementException) {
            val errorMessage = "No available versions found when parsing HIL dependency report: ${e.localizedMessage}"
            telemetry.error(errorMessage)
            LOG.error { errorMessage }
            return null
        } catch (e: Exception) {
            val errorMessage = "Unexpected error when parsing HIL dependency report: ${e.localizedMessage}"
            telemetry.error(errorMessage)
            LOG.error { errorMessage }
            return null
        }
    }

    fun rejectHil() {
        try {
            codeTransformationSession?.rejectHilAndContinue()
        } catch (e: Exception) {
            throw e
        } finally {
            // DO clean up
            codeTransformationSession?.hilCleanup()
        }
    }

    fun copyDependencyForHil(selectedVersion: String): MavenCopyCommandsResult {
        try {
            val session = codeTransformationSession ?: throw CodeModernizerException("Cannot get the current session")

            val tmpDirPath = session.getHilTempDirectoryPath()
                ?: throw CodeModernizerException("Cannot get HIL temp directory")

            val downloadedPomPath = getPathToHilArtifactPomFile(tmpDirPath)
            if (!downloadedPomPath.exists()) {
                return MavenCopyCommandsResult.Failure
            }

            val downloadedPomFile = File(downloadedPomPath.pathString)
            setDependencyVersionInPom(downloadedPomFile, selectedVersion)

            val copyDependencyResult = session.copyHilDependencyUsingMaven()
            return copyDependencyResult
        } catch (e: Exception) {
            val errorMessage = "Unexpected error when getting HIL dependency for upload: ${e.localizedMessage}"
            telemetry.error(errorMessage)
            LOG.error { errorMessage }
            return MavenCopyCommandsResult.Failure
        }
    }

    suspend fun tryResumeWithAlternativeVersion(selectedVersion: String) {
        try {
            val zipCreationResult = codeTransformationSession?.createHilUploadZip(selectedVersion)
            if (zipCreationResult?.payload?.exists() == true) {
                codeTransformationSession?.uploadHilPayload(zipCreationResult.payload)

                // Add delay between upload complete and trying to resume
                delay(500)

                codeTransformationSession?.resumeTransformFromHil()
            } else {
                throw CodeModernizerException("Cannot create dependency zip for HIL")
            }
        } catch (e: Exception) {
            val errorMessage = "Unexpected error when resuming HIL: ${e.localizedMessage}"
            telemetry.error(errorMessage)
            LOG.error { errorMessage }
        } finally {
            // DO file clean up
            codeTransformationSession?.hilCleanup()
        }
    }

    fun showHilPomFileAnnotation(): Boolean {
        val sourceVersion = codeTransformationSession?.getHilDownloadArtifact()?.manifest?.sourcePomVersion as String
        val dependencyReportDirPath = getPathToHilDependencyReportDir(codeTransformationSession?.getHilTempDirectoryPath() as Path)

        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(dependencyReportDirPath.resolve(HIL_POM_FILE_NAME).toFile())
        if (virtualFile != null) {
            val lineNumberToHighlight = findLineNumberByString(virtualFile, "<version>$sourceVersion</version>")
            val pomFileAnnotator = PomFileAnnotator(project, virtualFile, lineNumberToHighlight)
            pomFileAnnotator.showCustomEditor() // opens editor using Edt thread
        } else {
            return false
        }

        return true
    }

    /**
     * When customer attempts to download an artifact and it fails for some reason (credential expiry etc)
     * we need to be able to resume the job in order for customers to be able to reattempt the download.
     * This sets the job as ongoing in the persistent state so that when tryResumeJob is triggered the
     * IDE attempts to resume the job.
     */
    fun handleResumableDownloadArtifactFailure(job: JobId) {
        // handle the case when user clicks long living notification but has a new job running
        val session = this.codeTransformationSession ?: return
        if (session.getActiveJobId() != job) return
        setJobOngoing(job, session.sessionContext)
    }
}
