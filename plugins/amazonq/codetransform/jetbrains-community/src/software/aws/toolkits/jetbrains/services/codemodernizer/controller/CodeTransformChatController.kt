// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.controller

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLFileType
import software.amazon.awssdk.services.codewhispererstreaming.model.TransformationDownloadArtifactType
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.codemodernizer.ArtifactHandler
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager.Companion.LOG
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeTransformTelemetryManager
import software.aws.toolkits.jetbrains.services.codemodernizer.HilTelemetryMetaData
import software.aws.toolkits.jetbrains.services.codemodernizer.InboundAppMessagesHandler
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformActionMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformCommand
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildAbsolutePathWarning
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCheckingValidProjectChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildChooseTransformationObjectiveChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileHilAlternativeVersionContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalFailedChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalFailedNoJdkChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalInProgressChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalSuccessChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildContinueTransformationChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCustomDependencyVersionsFileInvalidChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCustomDependencyVersionsFileValidChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildDownloadFailureChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildHilCannotResumeContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildHilErrorContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildHilInitialContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildHilRejectContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildHilResumeWithErrorContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildHilResumedContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildInvalidTargetJdkNameChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildLanguageUpgradeProjectValidChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildModuleSchemaFormChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildModuleSchemaFormIntroChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildProjectInvalidChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildPromptTargetJDKNameChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildSQLMetadataValidationErrorChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildSQLMetadataValidationSuccessDetailsChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildSQLMetadataValidationSuccessIntroChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildStartNewTransformFollowup
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformAwaitUserInputChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformBeginChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformDependencyErrorChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformFailedChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformFindingLocalAlternativeDependencyChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformInProgressChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformResultChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformResumingChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformStoppedChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformStoppingChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserCancelledChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserHilSelection
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserInputCustomDependencyVersionsChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserInputLanguageUpgradeChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserInputSQLConversionMetadataChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserInputSkipTestsFlagChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserInputSkipTestsFlagChatIntroContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserLanguageUpgradeSelectionSummaryChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserReplyChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserSQLConversionSelectionSummaryChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserSkipTestsFlagSelectionChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserStopTransformChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.AuthenticationNeededExceptionMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformCommandMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.IncomingCodeTransformMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CLIENT_SIDE_BUILD
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerArtifact
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformConversationState
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformHilDownloadArtifact
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformType
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CustomerSelection
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DownloadArtifactResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DownloadFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.EXPLAINABILITY_V1
import software.aws.toolkits.jetbrains.services.codemodernizer.model.IDE
import software.aws.toolkits.jetbrains.services.codemodernizer.model.InvalidTelemetryReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_BUILD_RUN_UNIT_TESTS
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_BUILD_SKIP_UNIT_TESTS
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenDependencyReportCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.SELECTIVE_TRANSFORMATION_V2
import software.aws.toolkits.jetbrains.services.codemodernizer.model.UploadFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ValidationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.panels.managers.CodeModernizerBottomWindowPanelManager
import software.aws.toolkits.jetbrains.services.codemodernizer.session.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.codemodernizer.session.Session
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeModernizerSessionState
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getJavaModulesWithSQL
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getModuleOrProjectNameForFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.toVirtualFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.tryGetJdk
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.unzipFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.validateCustomVersionsFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.validateSctMetadata
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeTransformPreValidationError

class CodeTransformChatController(
    private val context: AmazonQAppInitContext,
    private val chatSessionStorage: ChatSessionStorage,
) : InboundAppMessagesHandler {
    private val authController = AuthController()
    private val messagePublisher = context.messagesFromAppToUi
    private val codeTransformChatHelper = CodeTransformChatHelper(context.messagesFromAppToUi, chatSessionStorage)
    private val codeModernizerManager = CodeModernizerManager.getInstance(context.project)
    private val artifactHandler = ArtifactHandler(context.project, GumbyClient.getInstance(context.project), codeTransformChatHelper)
    private val telemetry = CodeTransformTelemetryManager.getInstance(context.project)

    override suspend fun processChatPromptMessage(message: IncomingCodeTransformMessage.ChatPrompt) {
        if (chatSessionStorage.getSession(message.tabId).conversationState == CodeTransformConversationState.PROMPT_TARGET_JDK_NAME) {
            // we are prompting user for target JDK name
            processJDKNameChatPromptMessage(message)
            return
        }

        // otherwise, we are asking for transformation objective
        val objective = message.message.trim().lowercase()

        codeTransformChatHelper.addNewMessage(buildUserReplyChatContent(objective))
        codeTransformChatHelper.sendChatInputEnabledMessage(message.tabId, false)
        codeTransformChatHelper.sendUpdatePlaceholderMessage(message.tabId, "Open a new tab to chat with Q")

        // since we're prompting the user, their module(s) must be eligible for both types of transformations, so track how often this happens here
        if (objective == "language upgrade" || objective == "sql conversion") {
            telemetry.submitSelection(objective)
        }
        when (objective) {
            "language upgrade" -> this.handleLanguageUpgrade()
            "sql conversion" -> this.handleSQLConversion()
            else -> this.getUserObjective(message.tabId) // ask user again for objective
        }
    }

    override suspend fun processTransformQuickAction(message: IncomingCodeTransformMessage.Transform) {
        telemetry.prepareForNewJobSubmission()

        if (!checkForAuth(message.tabId)) {
            telemetry.initiateTransform("User is not authenticated")
            return
        }

        codeTransformChatHelper.setActiveCodeTransformTabId(message.tabId)

        if (!message.startNewTransform) {
            if (tryRestoreChatProgress()) {
                return
            }
        }

        // Publish a metric when transform is first initiated from chat prompt.
        telemetry.initiateTransform()

        val anyModuleContainsOracleSQL = codeModernizerManager.validate(context.project, CodeTransformType.SQL_CONVERSION).valid

        if (!anyModuleContainsOracleSQL) {
            this.handleLanguageUpgrade()
            return
        }

        val eligibleForLanguageUpgrade = codeModernizerManager.validate(context.project, CodeTransformType.LANGUAGE_UPGRADE).valid

        if (!eligibleForLanguageUpgrade) {
            this.handleSQLConversion()
            return
        }

        // eligible for both language upgrade and sql conversion, so ask user what they want to do
        this.getUserObjective(message.tabId)
    }

    private suspend fun getUserObjective(tabId: String) {
        chatSessionStorage.getSession(tabId).conversationState = CodeTransformConversationState.PROMPT_OBJECTIVE
        codeTransformChatHelper.addNewMessage(buildChooseTransformationObjectiveChatContent())
        codeTransformChatHelper.sendChatInputEnabledMessage(tabId, true)
        codeTransformChatHelper.sendUpdatePlaceholderMessage(tabId, message("codemodernizer.chat.message.choose_objective_placeholder"))
    }

    private suspend fun validateAndReplyOnError(transformationType: CodeTransformType): ValidationResult? {
        codeTransformChatHelper.addNewMessage(
            buildCheckingValidProjectChatContent()
        )

        codeTransformChatHelper.chatDelayShort()

        val validationResult = codeModernizerManager.validate(context.project, transformationType)

        if (!validationResult.valid) {
            codeTransformChatHelper.updateLastPendingMessage(
                buildProjectInvalidChatContent(validationResult)
            )
            codeTransformChatHelper.addNewMessage(
                buildStartNewTransformFollowup()
            )
            return null
        }
        return validationResult
    }

    private suspend fun handleSQLConversion() {
        telemetry.submitSelection("sql conversion")
        this.validateAndReplyOnError(CodeTransformType.SQL_CONVERSION) ?: return
        codeTransformChatHelper.addNewMessage(
            buildUserInputSQLConversionMetadataChatContent()
        )
    }

    private suspend fun handleLanguageUpgrade() {
        telemetry.submitSelection("language upgrade")
        val validationResult = this.validateAndReplyOnError(CodeTransformType.LANGUAGE_UPGRADE) ?: return
        codeTransformChatHelper.updateLastPendingMessage(
            buildLanguageUpgradeProjectValidChatContent()
        )
        codeTransformChatHelper.chatDelayShort()
        codeTransformChatHelper.addNewMessage(
            buildUserInputLanguageUpgradeChatContent(context.project, validationResult)
        )
    }

    private suspend fun tryRestoreChatProgress(): Boolean {
        val isTransformOngoing = codeModernizerManager.isModernizationJobActive()
        val isMvnRunning = codeModernizerManager.isRunningMvn()

        while (codeModernizerManager.isModernizationJobResuming()) {
            // Poll until transformation is resumed
            delay(50)
        }

        if (isMvnRunning) {
            codeTransformChatHelper.addNewMessage(buildCompileLocalInProgressChatContent())
            return true
        }

        if (isTransformOngoing) {
            if (codeModernizerManager.isJobSuccessfullyResumed()) {
                codeTransformChatHelper.addNewMessage(buildTransformResumingChatContent())
                codeTransformChatHelper.addNewMessage(buildTransformInProgressChatContent())
            } else {
                codeTransformChatHelper.addNewMessage(buildTransformBeginChatContent())
                codeTransformChatHelper.addNewMessage(buildTransformInProgressChatContent())
            }
            return true
        }

        val lastTransformResult = codeModernizerManager.getLastTransformResult()
        if (lastTransformResult != null) {
            codeTransformChatHelper.addNewMessage(buildTransformResumingChatContent())
            handleCodeTransformResult(lastTransformResult)
            return true
        }

        val lastMvnBuildResult = codeModernizerManager.getLastMvnBuildResult()
        if (lastMvnBuildResult != null) {
            codeTransformChatHelper.addNewMessage(buildCompileLocalInProgressChatContent())
            handleMavenBuildResult(lastMvnBuildResult)
            return true
        }

        return false
    }

    override suspend fun processCodeTransformCancelAction(message: IncomingCodeTransformMessage.CodeTransformCancel) {
        if (!checkForAuth(message.tabId)) {
            telemetry.submitSelection("Cancel", null, null, "User is not authenticated")
            return
        }

        // Publish metric for user selection
        telemetry.submitSelection("Cancel")

        codeTransformChatHelper.run {
            addNewMessage(buildUserCancelledChatContent())
            addNewMessage(buildStartNewTransformFollowup())
        }
    }

    override suspend fun processCodeTransformStartAction(message: IncomingCodeTransformMessage.CodeTransformStart) {
        if (!checkForAuth(message.tabId)) {
            telemetry.submitSelection("Confirm", null, null, "User is not authenticated")
            return
        }

        val (tabId, modulePath, targetVersion) = message

        val moduleVirtualFile: VirtualFile = modulePath.toVirtualFile() as VirtualFile
        val moduleName = context.project.getModuleOrProjectNameForFile(moduleVirtualFile)

        codeTransformChatHelper.addNewMessage(buildUserLanguageUpgradeSelectionSummaryChatContent(moduleName, targetVersion))

        val sourceJdk = getSourceJdk(moduleVirtualFile)

        val sourceVersion = sourceJdk.toString()

        if (sourceVersion == JavaSdkVersion.JDK_21.toString() && targetVersion == JavaSdkVersion.JDK_17.toString()) {
            codeTransformChatHelper.addNewMessage(
                buildProjectInvalidChatContent(
                    ValidationResult(
                        false,
                        InvalidTelemetryReason(CodeTransformPreValidationError.JavaDowngradeAttempt)
                    )
                )
            )
            return
        }

        val selection = CustomerSelection(
            configurationFile = moduleVirtualFile,
            sourceJavaVersion = sourceJdk,
            targetJavaVersion = if (targetVersion == JavaSdkVersion.JDK_17.toString()) JavaSdkVersion.JDK_17 else JavaSdkVersion.JDK_21,
        )

        // Create and set a session
        codeModernizerManager.createCodeModernizerSession(selection, context.project)

        // Publish metric to capture user selection before local build starts
        telemetry.submitSelection("Confirm-Java", null, selection)

        codeTransformChatHelper.run {
            addNewMessage(buildUserInputSkipTestsFlagChatIntroContent())
            addNewMessage(buildUserInputSkipTestsFlagChatContent())
        }
    }

    override suspend fun processCodeTransformSelectSQLModuleSchemaAction(message: IncomingCodeTransformMessage.CodeTransformSelectSQLModuleSchema) {
        val moduleName = context.project.getModuleOrProjectNameForFile(message.modulePath.toVirtualFile())
        codeTransformChatHelper.addNewMessage(buildUserSQLConversionSelectionSummaryChatContent(moduleName, message.schema))
        codeModernizerManager.codeTransformationSession?.let {
            it.sessionContext.configurationFile = message.modulePath.toVirtualFile()
            it.sessionContext.schema = message.schema
        }
        // start the SQL conversion
        runInEdt {
            codeModernizerManager.runModernize()
        }
    }

    override suspend fun processCodeTransformSelectSQLMetadataAction(message: IncomingCodeTransformMessage.CodeTransformSelectSQLMetadata) {
        withContext(EDT) {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withDescription("Select metadata file")

            val selectedZipFile = FileChooser.chooseFile(descriptor, null, null) ?: return@withContext
            val extractedZip = createTempDirectory("codeTransformSQLMetadata", null)

            unzipFile(selectedZipFile.toNioPath(), extractedZip.toPath(), true)

            val sctFile = extractedZip.listFiles { file -> file.name.endsWith(".sct") }?.firstOrNull()

            val metadataValidationResult = validateSctMetadata(sctFile)

            if (!metadataValidationResult.valid) {
                codeTransformChatHelper.run {
                    addNewMessage(buildSQLMetadataValidationErrorChatContent(metadataValidationResult.errorReason))
                    addNewMessage(buildStartNewTransformFollowup())
                }
                return@withContext
            }

            codeTransformChatHelper.run {
                addNewMessage(buildSQLMetadataValidationSuccessIntroChatContent())
                addNewMessage(buildSQLMetadataValidationSuccessDetailsChatContent(metadataValidationResult))
                addNewMessage(buildModuleSchemaFormIntroChatContent())
                addNewMessage(
                    buildModuleSchemaFormChatContent(context.project, context.project.getJavaModulesWithSQL(), metadataValidationResult.schemaOptions)
                )
            }
            val selection = CustomerSelection(
                // for SQL conversions (no sourceJavaVersion), use dummy value of Java 8 so that startJob API can be called
                sourceJavaVersion = JavaSdkVersion.JDK_1_8,
                targetJavaVersion = JavaSdkVersion.JDK_17,
                sourceVendor = metadataValidationResult.sourceVendor,
                targetVendor = metadataValidationResult.targetVendor,
                sourceServerName = metadataValidationResult.sourceServerName,
                sqlMetadataZip = extractedZip,
            )
            codeModernizerManager.createCodeModernizerSession(selection, context.project)
            telemetry.submitSelection("Confirm-SQL", null, selection)
        }
    }

    override suspend fun processCodeTransformConfirmSkipTests(message: IncomingCodeTransformMessage.CodeTransformConfirmSkipTests) {
        val customBuildCommand = when (message.skipTestsSelection) {
            message("codemodernizer.chat.message.skip_tests_form.skip") -> MAVEN_BUILD_SKIP_UNIT_TESTS
            else -> MAVEN_BUILD_RUN_UNIT_TESTS
        }
        codeTransformChatHelper.addNewMessage(buildUserSkipTestsFlagSelectionChatContent(message.skipTestsSelection))
        codeModernizerManager.codeTransformationSession?.let {
            it.sessionContext.customBuildCommand = customBuildCommand
        }
        val transformCapabilities = listOf(EXPLAINABILITY_V1, CLIENT_SIDE_BUILD, SELECTIVE_TRANSFORMATION_V2)
        codeModernizerManager.codeTransformationSession?.let {
            it.sessionContext.transformCapabilities = transformCapabilities
        }
        promptForCustomYamlFile()
    }

    override suspend fun processCodeTransformCustomDependencyVersions(message: IncomingCodeTransformMessage.CodeTransformConfirmCustomDependencyVersions) {
        withContext(EDT) {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withDescription("Select .yaml file")
            val selectedFile = FileChooser.chooseFile(descriptor, null, null) ?: return@withContext
            val isValid = validateCustomVersionsFile(selectedFile)
            if (!isValid) {
                codeTransformChatHelper.updateLastPendingMessage(buildCustomDependencyVersionsFileInvalidChatContent())
                codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
                return@withContext
            }
            codeModernizerManager.codeTransformationSession?.let {
                it.sessionContext.customDependencyVersionsFile = selectedFile
            }
            codeTransformChatHelper.updateLastPendingMessage(buildCustomDependencyVersionsFileValidChatContent())
            promptForTargetJdkName(message.tabId)
        }
    }

    private suspend fun processJDKNameChatPromptMessage(message: IncomingCodeTransformMessage.ChatPrompt) {
        chatSessionStorage.getSession(message.tabId).conversationState = CodeTransformConversationState.IDLE
        codeTransformChatHelper.sendChatInputEnabledMessage(message.tabId, false)
        codeTransformChatHelper.sendUpdatePlaceholderMessage(message.tabId, "")

        val providedJdkName = message.message.trim().lowercase()
        val targetJdkName = ProjectJdkTable.getInstance().allJdks.find { it.name.trim().lowercase() == providedJdkName }?.name
        if (targetJdkName == null) {
            codeTransformChatHelper.addNewMessage(buildInvalidTargetJdkNameChatContent(providedJdkName))
            return
        }
        codeModernizerManager.codeTransformationSession?.sessionContext?.targetJdkName = targetJdkName
        codeTransformChatHelper.addNewMessage(buildUserReplyChatContent(message.message.trim()))
        // start local build once we get target JDK path
        codeTransformChatHelper.addNewMessage(buildCompileLocalInProgressChatContent())
        codeModernizerManager.codeTransformationSession?.let {
            codeModernizerManager.runLocalMavenBuild(context.project, it)
        }
    }

    private suspend fun promptForCustomYamlFile() {
        codeTransformChatHelper.addNewMessage(buildUserInputCustomDependencyVersionsChatContent())
        val sampleYAML = """
name: "dependency-upgrade"
description: "Custom dependency version management for Java migration from JDK 8/11/17 to JDK 17/21"
dependencyManagement:
  dependencies:
    - identifier: "com.example:library1"
      targetVersion: "2.1.0"
      versionProperty: "library1.version"  # Optional
      originType: "FIRST_PARTY" # or "THIRD_PARTY"
    - identifier: "com.example:library2"
      targetVersion: "3.0.0"
      originType: "THIRD_PARTY"
  plugins:
    - identifier: "com.example:plugin"
      targetVersion: "1.2.0"
      versionProperty: "plugin.version"  # Optional
        """.trimIndent()

        val virtualFile = LightVirtualFile("dependency_upgrade.yml", YAMLFileType.YML, sampleYAML)
        virtualFile.isWritable = true
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(context.project).openFile(virtualFile, true)
        }
    }

    override suspend fun processCodeTransformContinueAction(message: IncomingCodeTransformMessage.CodeTransformContinue) {
        codeTransformChatHelper.addNewMessage(buildContinueTransformationChatContent())
        promptForTargetJdkName(message.tabId)
    }

    private suspend fun promptForTargetJdkName(tabId: String) {
        chatSessionStorage.getSession(tabId).conversationState = CodeTransformConversationState.PROMPT_TARGET_JDK_NAME
        val targetJdkVersion = codeModernizerManager.codeTransformationSession?.sessionContext?.targetJavaVersion?.name.orEmpty()
        codeTransformChatHelper.addNewMessage(buildPromptTargetJDKNameChatContent(targetJdkVersion))
        codeTransformChatHelper.sendChatInputEnabledMessage(tabId, true)
        codeTransformChatHelper.sendUpdatePlaceholderMessage(tabId, "Enter the name of your $targetJdkVersion")
    }

    private fun getSourceJdk(moduleConfigurationFile: VirtualFile): JavaSdkVersion {
        // this should never throw the RuntimeException since invalid JDK case is already handled in previous validation step
        val moduleJdkVersion = ModuleUtil.findModuleForFile(moduleConfigurationFile, context.project)?.tryGetJdk(context.project)
        logger.info { "Found project JDK version: ${context.project.tryGetJdk()}, module JDK version: $moduleJdkVersion. Module JDK version prioritized." }
        val sourceJdk = moduleJdkVersion ?: context.project.tryGetJdk() ?: error("Unable to determine source JDK version")
        return sourceJdk
    }

    private suspend fun handleMavenBuildResult(mavenBuildResult: MavenCopyCommandsResult) {
        when (mavenBuildResult) {
            MavenCopyCommandsResult.Cancelled -> {
                codeTransformChatHelper.updateLastPendingMessage(buildUserCancelledChatContent())
                codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
                return
            }
            MavenCopyCommandsResult.Failure -> {
                codeTransformChatHelper.updateLastPendingMessage(buildCompileLocalFailedChatContent())
                codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
                return
            }
            MavenCopyCommandsResult.NoJdk -> {
                codeTransformChatHelper.updateLastPendingMessage(buildCompileLocalFailedNoJdkChatContent())
                codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
                return
            }

            is MavenCopyCommandsResult.Success -> {
                // proceed with transformation
            }
        }

        codeTransformChatHelper.run {
            updateLastPendingMessage(buildCompileLocalSuccessChatContent())
        }

        // show user a non-blocking warning if their build file contains an absolute path
        try {
            val warningMessage = codeModernizerManager.parseBuildFile()
            if (warningMessage != null) {
                handleAbsolutePathDetected(warningMessage)
            }
        } catch (e: Exception) {
            // swallow error and move on
        }

        runInEdt {
            codeModernizerManager.runModernize(mavenBuildResult)
        }
    }

    override suspend fun processCodeTransformStopAction(tabId: String) {
        if (!checkForAuth(tabId) || !codeModernizerManager.isModernizationJobActive()) {
            return
        }

        updatePomPreviewItem()

        codeTransformChatHelper.run {
            addNewMessage(buildUserStopTransformChatContent())

            addNewMessage(buildTransformStoppingChatContent())
        }

        runBlocking {
            codeModernizerManager.stopModernize()
        }
    }

    override suspend fun processCodeTransformOpenTransformHub(message: IncomingCodeTransformMessage.CodeTransformOpenTransformHub) {
        runInEdt {
            codeModernizerManager.getBottomToolWindow().show()
        }
    }

    override suspend fun processCodeTransformOpenMvnBuild(message: IncomingCodeTransformMessage.CodeTransformOpenMvnBuild) {
        runInEdt {
            codeModernizerManager.getMvnBuildWindow().show()
        }
    }

    override suspend fun processCodeTransformViewDiff(message: IncomingCodeTransformMessage.CodeTransformViewDiff) {
        artifactHandler.displayDiffAction(
            CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId,
        )
    }

    override suspend fun processCodeTransformViewSummary(message: IncomingCodeTransformMessage.CodeTransformViewSummary) {
        artifactHandler.showTransformationSummary(CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId)
    }

    override suspend fun processCodeTransformViewBuildLog(message: IncomingCodeTransformMessage.CodeTransformViewBuildLog) {
        artifactHandler.showBuildLog(CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId)
    }

    override suspend fun processCodeTransformNewAction(message: IncomingCodeTransformMessage.CodeTransformNew) {
        processTransformQuickAction(IncomingCodeTransformMessage.Transform(tabId = message.tabId, startNewTransform = true))
    }

    /**
     * Invoking this is equivalent to user clicking "reauthenticate" in the Chat when credentials expired.
     */
    suspend fun handleReauthStarted(activeTabId: String) {
        if (isCodeTransformAvailable(context.project)) return
        processAuthFollowUpClick(IncomingCodeTransformMessage.AuthFollowUpWasClicked(activeTabId, AuthFollowUpType.ReAuth))
    }

    /**
     * Calls [checkForAuth] to verify auth status, if auth invalid informs [CodeModernizerManager] and [CodeModernizerBottomWindowPanelManager]
     * that auth changed to invalid.
     */
    suspend fun handleCheckAuth(activeTabId: String) {
        if (!checkForAuth(activeTabId)) {
            runInEdt {
                CodeModernizerBottomWindowPanelManager.getInstance(context.project).toolWindow?.isAvailable = isCodeTransformAvailable(context.project)
                CodeModernizerManager.getInstance(context.project).handleCredentialsChanged()
            }
        }
    }

    /**
     * This handles the actions needed when user is reauthenticated.
     * Attempts to resume the job if one was ongoing pre auth expiry or requests users to start a new transformation.
     */
    suspend fun handleAuthRestored() {
        if (!isCodeTransformAvailable(context.project)) return
        val manager = CodeModernizerManager.getInstance(context.project)
        manager.handleCredentialsChanged()
        if (manager.isJobOngoingInState()) {
            runInEdt {
                CodeModernizerBottomWindowPanelManager.getInstance(context.project).toolWindow?.isAvailable = true
                manager.tryResumeJob()
            }
        } else {
            codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
        }
    }

    override suspend fun processCodeTransformCommand(message: CodeTransformActionMessage) {
        var activeTabId = codeTransformChatHelper.getActiveCodeTransformTabId()
        activeTabId ?: logger.error { "in processCodeTransformCommand there is no tab active for CodeTransform: activeTabId == $activeTabId" }
        if (activeTabId == null && message.command == CodeTransformCommand.TransformResuming) {
            // If we are resuming a job, we should show transform progress also in chat, so open a tab if this is the case.
            codeTransformChatHelper.createNewCodeTransformTab()
            while (activeTabId == null) {
                activeTabId = codeTransformChatHelper.getActiveCodeTransformTabId()
            }
        }
        activeTabId ?: return

        when (message.command) {
            CodeTransformCommand.StopClicked -> {
                messagePublisher.publish(CodeTransformCommandMessage(command = "stop"))
                processCodeTransformStopAction(activeTabId)
            }
            CodeTransformCommand.MavenBuildComplete -> {
                val result = message.mavenBuildResult
                if (result != null) {
                    handleMavenBuildResult(result)
                }
            }
            CodeTransformCommand.UploadComplete -> handleCodeTransformUploadCompleted()
            CodeTransformCommand.TransformComplete -> {
                val result = message.transformResult
                if (result != null) {
                    handleCodeTransformResult(result)
                }
            }
            CodeTransformCommand.TransformStopped -> handleCodeTransformStoppedByUser()
            CodeTransformCommand.TransformResuming -> handleCodeTransformJobResume()
            CodeTransformCommand.StartHil -> handleHil()
            CodeTransformCommand.AuthRestored -> handleAuthRestored()
            CodeTransformCommand.ReauthStarted -> handleReauthStarted(activeTabId)
            CodeTransformCommand.CheckAuth -> handleCheckAuth(activeTabId)
            CodeTransformCommand.DownloadFailed -> {
                val result = message.downloadFailure
                if (result != null) {
                    handleDownloadFailed(message.downloadFailure)
                }
            }
        }
    }

    override suspend fun processTabCreated(message: IncomingCodeTransformMessage.TabCreated) {
        logger.debug { "$FEATURE_NAME: New tab created: $message" }
        codeTransformChatHelper.setActiveCodeTransformTabId(message.tabId)
    }

    /**
     * Return true if authenticated, else show authentication message and return false.
     */
    private suspend fun checkForAuth(tabId: String): Boolean {
        var session: Session? = null
        try {
            session = chatSessionStorage.getSession(tabId)
            logger.debug { "$FEATURE_NAME: Session created with id: ${session.tabId}" }

            val credentialState = authController.getAuthNeededStates(context.project).amazonQ
            if (credentialState != null) {
                messagePublisher.publish(
                    AuthenticationNeededExceptionMessage(
                        tabId = session.tabId,
                        authType = credentialState.authType,
                        message = credentialState.message
                    )
                )
                session.isAuthenticating = true
                return false
            }
        } catch (err: Exception) {
            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = tabId,
                    messageType = ChatMessageType.Answer,
                    message = message("codemodernizer.chat.message.error_request")
                )
            )
            return false
        }

        return true
    }

    override suspend fun processTabRemoved(message: IncomingCodeTransformMessage.TabRemoved) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processAuthFollowUpClick(message: IncomingCodeTransformMessage.AuthFollowUpWasClicked) {
        authController.handleAuth(context.project, message.authType)
        messagePublisher.publish(
            CodeTransformChatMessage(
                tabId = message.tabId,
                messageType = ChatMessageType.Answer,
                message = message("codemodernizer.chat.message.auth_prompt")
            )
        )
    }

    override suspend fun processBodyLinkClicked(message: IncomingCodeTransformMessage.BodyLinkClicked) {
        BrowserUtil.browse(message.link)
    }

    private suspend fun handleAbsolutePathDetected(warning: String) =
        codeTransformChatHelper.addNewMessage(buildAbsolutePathWarning(warning))

    private suspend fun handleCodeTransformUploadCompleted() {
        codeTransformChatHelper.addNewMessage(buildTransformBeginChatContent())
        codeTransformChatHelper.addNewMessage(buildTransformInProgressChatContent())
    }

    private suspend fun handleCodeTransformJobResume() {
        codeTransformChatHelper.addNewMessage(buildTransformResumingChatContent())
    }

    private suspend fun handleCodeTransformStoppedByUser() {
        codeTransformChatHelper.updateLastPendingMessage(buildTransformStoppedChatContent())
        codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
    }

    private suspend fun handleCodeTransformJobFailed(failureReason: String) {
        codeTransformChatHelper.updateLastPendingMessage(buildTransformFailedChatContent(failureReason))
        codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
    }

    private suspend fun handleCodeTransformJobFailedPreBuild(result: CodeModernizerJobCompletedResult.JobFailedInitialBuild) {
        codeTransformChatHelper.addNewMessage(
            buildTransformResultChatContent(result)
        )
        artifactHandler.showBuildLog(CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId)
    }

    private suspend fun handleCodeTransformResult(result: CodeModernizerJobCompletedResult) {
        LOG.info { "CodeModernizerJobCompletedResult: $result" }
        when (result) {
            is CodeModernizerJobCompletedResult.Stopped, CodeModernizerJobCompletedResult.JobAbortedBeforeStarting -> handleCodeTransformStoppedByUser()
            is CodeModernizerJobCompletedResult.JobFailed -> handleCodeTransformJobFailed(result.failureReason)
            is CodeModernizerJobCompletedResult.JobFailedInitialBuild -> handleCodeTransformJobFailedPreBuild(result)
            is CodeModernizerJobCompletedResult.RetryableFailure -> handleCodeTransformJobFailed(result.failureReason)
            is CodeModernizerJobCompletedResult.UnableToCreateJob -> handleCodeTransformJobFailed(result.failureReason)
            else -> {
                if (result is CodeModernizerJobCompletedResult.ZipUploadFailed && result.failureReason is UploadFailureReason.CREDENTIALS_EXPIRED) {
                    return
                } else if (CodeModernizerSessionState.getInstance(context.project).currentJobId != null) {
                    val downloadResult = artifactHandler.downloadArtifact(
                        CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId,
                        TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS
                    )
                    LOG.info { "Download result: $downloadResult" }
                    when (downloadResult) {
                        is DownloadArtifactResult.Success -> {
                            if (downloadResult.artifact !is CodeModernizerArtifact) return artifactHandler.notifyUnableToApplyPatch("")
                            codeTransformChatHelper.updateLastPendingMessage(
                                buildTransformResultChatContent(result)
                            )
                        }
                        is DownloadArtifactResult.DownloadFailure -> artifactHandler.notifyUnableToDownload(downloadResult.failureReason)
                        is DownloadArtifactResult.ParseZipFailure -> artifactHandler.notifyUnableToApplyPatch(downloadResult.failureReason.errorMessage)
                        is DownloadArtifactResult.Skipped -> {}
                        is DownloadArtifactResult.UnzipFailure -> artifactHandler.notifyUnableToApplyPatch(downloadResult.failureReason.errorMessage)
                    }
                }
            }
        }
    }

    private suspend fun hilTryResumeAfterError(errorMessage: String) {
        codeTransformChatHelper.addNewMessage(buildHilErrorContent(errorMessage))
        codeTransformChatHelper.addNewMessage(buildHilResumeWithErrorContent())

        try {
            codeModernizerManager.rejectHil()
            runInEdt {
                codeModernizerManager.getBottomToolWindow().show()
            }

            codeTransformChatHelper.chatDelayLong()

            codeModernizerManager.resumePollingFromHil()
        } catch (e: Exception) {
            telemetry.logHil(
                CodeModernizerSessionState.getInstance(context.project).currentJobId?.id.orEmpty(),
                HilTelemetryMetaData(
                    cancelledFromChat = false,
                ),
                success = false,
                reason = "Runtime Error when trying to resume transformation from HIL",
            )
            codeTransformChatHelper.updateLastPendingMessage(buildHilCannotResumeContent())
        }
    }

    private suspend fun handleHil() {
        codeTransformChatHelper.updateLastPendingMessage(buildHilInitialContent())

        val hilDownloadArtifact = codeModernizerManager.getArtifactForHil()

        if (hilDownloadArtifact == null) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.cannot_download_artifact"))
            return
        }

        codeTransformChatHelper.addNewMessage(buildTransformDependencyErrorChatContent(hilDownloadArtifact), codeTransformChatHelper.generateHilPomItemId())
        codeTransformChatHelper.addNewMessage(buildTransformFindingLocalAlternativeDependencyChatContent(), clearPreviousItemButtons = false)
        val createReportResult = codeModernizerManager.createDependencyReport(hilDownloadArtifact)
        if (createReportResult == MavenDependencyReportCommandsResult.Cancelled) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.cancel_dependency_search"))
            return
        } else if (createReportResult == MavenDependencyReportCommandsResult.Failure) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.no_other_versions_found", hilDownloadArtifact.manifest.pomArtifactId))
            return
        }

        val dependency = codeModernizerManager.findAvailableVersionForDependency(
            hilDownloadArtifact.manifest.pomGroupId,
            hilDownloadArtifact.manifest.pomArtifactId
        )

        if (dependency == null || (dependency.majors.isNullOrEmpty() && dependency.minors.isNullOrEmpty() && dependency.incrementals.isNullOrEmpty())) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.no_other_versions_found", hilDownloadArtifact.manifest.pomArtifactId))
            return
        }
        codeTransformChatHelper.updateLastPendingMessage(buildTransformAwaitUserInputChatContent(dependency))
        runInEdt {
            codeModernizerManager.getBottomToolWindow().show()
        }
    }

    private suspend fun handleDownloadFailed(failureReason: DownloadFailureReason) {
        val message = buildDownloadFailureChatContent(failureReason) ?: return
        codeTransformChatHelper.addNewMessage(message)
        codeTransformChatHelper.addNewMessage(buildStartNewTransformFollowup())
    }

    // Remove open file button after pom.xml is deleted
    private suspend fun updatePomPreviewItem() {
        val hilPomItemId = codeTransformChatHelper.getHilPomItemId() ?: return
        val hilDownloadArtifact = codeModernizerManager.getArtifactForHil()
        if (hilDownloadArtifact != null) {
            codeTransformChatHelper.updateExistingMessage(
                hilPomItemId,
                buildTransformDependencyErrorChatContent(hilDownloadArtifact, false)
            )
        }
        codeTransformChatHelper.clearHilPomItemId()
    }

    override suspend fun processConfirmHilSelection(message: IncomingCodeTransformMessage.ConfirmHilSelection) {
        if (!checkForAuth(message.tabId)) {
            return
        }

        updatePomPreviewItem()

        val selectedVersion = message.version
        val artifact = codeModernizerManager.getCurrentHilArtifact() as CodeTransformHilDownloadArtifact

        codeTransformChatHelper.run {
            addNewMessage(buildUserHilSelection(artifact.manifest.pomArtifactId, artifact.manifest.sourcePomVersion, selectedVersion))

            addNewMessage(buildCompileHilAlternativeVersionContent())
        }

        val copyDependencyResult = codeModernizerManager.copyDependencyForHil(selectedVersion)
        if (copyDependencyResult == MavenCopyCommandsResult.Failure) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.cannot_upload"))
            return
        } else if (copyDependencyResult == MavenCopyCommandsResult.Cancelled) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.cancel_upload"))
            return
        }

        try {
            codeModernizerManager.tryResumeWithAlternativeVersion(selectedVersion)

            telemetry.logHil(
                CodeModernizerSessionState.getInstance(context.project).currentJobId?.id as String,
                HilTelemetryMetaData(
                    dependencyVersionSelected = selectedVersion,
                ),
                success = true,
                reason = "User selected version"
            )

            codeTransformChatHelper.updateLastPendingMessage(buildHilResumedContent())

            runInEdt {
                codeModernizerManager.getBottomToolWindow().show()
            }
            codeModernizerManager.resumePollingFromHil()
        } catch (e: Exception) {
            hilTryResumeAfterError(message("codemodernizer.chat.message.hil.error.cannot_upload"))
        }
    }

    override suspend fun processRejectHilSelection(message: IncomingCodeTransformMessage.RejectHilSelection) {
        if (!checkForAuth(message.tabId)) {
            return
        }

        codeTransformChatHelper.addNewMessage(buildHilRejectContent())

        updatePomPreviewItem()

        try {
            codeModernizerManager.rejectHil()

            telemetry.logHil(
                CodeModernizerSessionState.getInstance(context.project).currentJobId?.id.orEmpty(),
                HilTelemetryMetaData(
                    cancelledFromChat = true,
                ),
                success = false,
                reason = "User cancelled"
            )

            runInEdt {
                codeModernizerManager.getBottomToolWindow().show()
            }
            codeModernizerManager.resumePollingFromHil()
        } catch (e: Exception) {
            telemetry.logHil(
                CodeModernizerSessionState.getInstance(context.project).currentJobId?.id.orEmpty(),
                HilTelemetryMetaData(
                    cancelledFromChat = false,
                ),
                success = false,
                reason = "Runtime Error when trying to resume transformation from HIL",
            )
            codeTransformChatHelper.updateLastPendingMessage(buildHilCannotResumeContent())
        }
    }

    override suspend fun processOpenPomFileHilClicked(message: IncomingCodeTransformMessage.OpenPomFileHilClicked) {
        if (!checkForAuth(message.tabId)) {
            return
        }

        try {
            codeModernizerManager.showHilPomFileAnnotation()
        } catch (e: Exception) {
            telemetry.error("Unknown exception when trying to open hil pom file: ${e.localizedMessage}")
            logger.error { "Unknown exception when trying to open file: ${e.localizedMessage}" }
        }
    }

    companion object {
        private val logger = getLogger<CodeTransformChatController>()
    }
}
