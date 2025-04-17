// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.constants

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.codewhispererstreaming.model.TransformationDownloadArtifactType
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_PREREQUISITES
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_ALLOW_S3_ACCESS
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_CONFIGURE_PROXY
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_ERROR_OVERVIEW
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_EXPIRED
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_PROJECT_SIZE
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_REMOVE_WILDCARD
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC_UPLOAD_ERROR_OVERVIEW
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.Button
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformButtonId
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessageContent
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessageType
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformFormItemId
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.FormItem
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.FormItemOption
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformHilDownloadArtifact
import software.aws.toolkits.jetbrains.services.codemodernizer.model.Dependency
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DownloadFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.SqlMetadataValidationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.UploadFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ValidationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getModuleOrProjectNameForFile
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.FollowUpType
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeTransformPreValidationError

// shared Cancel button
private val cancelUserSelectionButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = false,
    text = message("codemodernizer.chat.message.button.cancel"),
    id = CodeTransformButtonId.CancelTransformation.id,
)

// used to continue transformation without providing custom YAML file
private val continueTransformationButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = false,
    text = "Continue without this",
    id = CodeTransformButtonId.ContinueTransformation.id,
)

private val confirmUserSelectionLanguageUpgradeButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
    text = message("codemodernizer.chat.message.button.confirm"),
    id = CodeTransformButtonId.StartTransformation.id,
)

private val confirmUserSelectionSQLConversionModuleSchemaButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
    text = message("codemodernizer.chat.message.button.confirm"),
    id = CodeTransformButtonId.SelectSQLModuleSchema.id,
)

private val confirmUserSelectionSQLConversionMetadataButton = Button(
    keepCardAfterClick = true,
    waitMandatoryFormItems = true,
    text = message("codemodernizer.chat.message.button.select_sql_metadata"),
    id = CodeTransformButtonId.SelectSQLMetadata.id,
)

private val confirmSkipTestsSelectionButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
    text = message("codemodernizer.chat.message.button.confirm"),
    id = CodeTransformButtonId.ConfirmSkipTests.id,
)

private val confirmOneOrMultipleDiffsSelectionButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
    text = message("codemodernizer.chat.message.button.confirm"),
    id = CodeTransformButtonId.ConfirmOneOrMultipleDiffs.id,
)

private val confirmCustomDependencyVersionsButton = Button(
    keepCardAfterClick = true,
    waitMandatoryFormItems = true, // TODO: what does this do?
    text = "Select file",
    id = CodeTransformButtonId.ConfirmCustomDependencyVersions.id,
)

private val openMvnBuildButton = Button(
    id = CodeTransformButtonId.OpenMvnBuild.id,
    text = message("codemodernizer.chat.message.button.view_build"),
    keepCardAfterClick = true,
)

private val stopTransformButton = Button(
    id = CodeTransformButtonId.StopTransformation.id,
    text = message("codemodernizer.chat.message.button.stop_transform"),
    keepCardAfterClick = true,
)

private val openTransformHubButton = Button(
    id = CodeTransformButtonId.OpenTransformationHub.id,
    text = message("codemodernizer.chat.message.button.open_transform_hub"),
    keepCardAfterClick = true,
)

private val viewDiffButton = Button(
    id = CodeTransformButtonId.ViewDiff.id,
    text = message("codemodernizer.chat.message.button.view_diff"),
    keepCardAfterClick = true,
)

fun createViewDiffButton(buttonLabel: String): Button = Button(
    id = CodeTransformButtonId.ViewDiff.id,
    text = buttonLabel,
    keepCardAfterClick = true
)

val viewSummaryButton = Button(
    id = CodeTransformButtonId.ViewSummary.id,
    text = message("codemodernizer.chat.message.button.view_summary"),
    keepCardAfterClick = true,
)

private val viewBuildLog = Button(
    id = CodeTransformButtonId.ViewBuildLog.id,
    text = message("codemodernizer.chat.message.button.view_failure_build_log"),
    keepCardAfterClick = true,
)

private val confirmHilSelectionButton = Button(
    id = CodeTransformButtonId.ConfirmHilSelection.id,
    text = message("codemodernizer.chat.message.button.hil_submit"),
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
)

private val rejectHilSelectionButton = Button(
    id = CodeTransformButtonId.RejectHilSelection.id,
    text = message("codemodernizer.chat.message.button.hil_cancel"),
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
)

private val openDependencyErrorPomFileButton = Button(
    id = CodeTransformButtonId.OpenDependencyErrorPom.id,
    text = message("codemodernizer.chat.message.button.open_file"),
    keepCardAfterClick = true,
)

private val startNewTransformFollowUp = FollowUp(
    type = FollowUpType.NewCodeTransform,
    pillText = message("codemodernizer.chat.message.follow_up.new_transformation"),
    prompt = message("codemodernizer.chat.message.follow_up.new_transformation"),
)

private fun getSelectModuleFormItem(project: Project, moduleBuildFiles: List<VirtualFile>) = FormItem(
    id = CodeTransformFormItemId.SelectModule.id,
    title = message("codemodernizer.chat.form.user_selection.item.choose_module"),
    mandatory = true,
    options = moduleBuildFiles.map {
        FormItemOption(
            label = project.getModuleOrProjectNameForFile(it),
            value = it.path,
        )
    }
)

private fun getSelectSQLModuleFormItem(project: Project, javaModules: List<VirtualFile>) = FormItem(
    id = CodeTransformFormItemId.SelectModule.id,
    title = message("codemodernizer.chat.form.user_selection.item.choose_module"),
    mandatory = true,
    options = javaModules.map {
        FormItemOption(
            label = project.getModuleOrProjectNameForFile(it),
            value = it.path,
        )
    }
)

private val selectTargetVersionFormItem = FormItem(
    id = CodeTransformFormItemId.SelectTargetVersion.id,
    title = message("codemodernizer.chat.form.user_selection.item.choose_target_version"),
    mandatory = true,
    options = listOf(
        FormItemOption(
            label = JavaSdkVersion.JDK_17.toString(),
            value = JavaSdkVersion.JDK_17.toString(),
        ),
        FormItemOption(
            label = JavaSdkVersion.JDK_21.toString(),
            value = JavaSdkVersion.JDK_21.toString(),
        )
    )
)

private fun getSelectSQLSchemaFormItem(schemaOptions: Set<String>) = FormItem(
    id = CodeTransformFormItemId.SelectSQLSchema.id,
    title = "Choose the schema",
    mandatory = true,
    options = schemaOptions.map {
        FormItemOption(
            label = it,
            value = it,
        )
    }
)

private val selectSkipTestsFlagFormItem = FormItem(
    id = CodeTransformFormItemId.SelectSkipTestsFlag.id,
    title = message("codemodernizer.chat.form.user_selection.item.choose_skip_tests_option"),
    mandatory = true,
    options = listOf(
        FormItemOption(
            label = message("codemodernizer.chat.message.skip_tests_form.skip"),
            value = message("codemodernizer.chat.message.skip_tests_form.skip"),
        ),
        FormItemOption(
            label = message("codemodernizer.chat.message.skip_tests_form.run_tests"),
            value = message("codemodernizer.chat.message.skip_tests_form.run_tests"),
        ),
    )
)

private val selectOneOrMultipleDiffsFlagFormItem = FormItem(
    id = CodeTransformFormItemId.SelectOneOrMultipleDiffsFlag.id,
    title = message("codemodernizer.chat.form.user_selection.item.choose_one_or_multiple_diffs_option"),
    mandatory = true,
    options = listOf(
        FormItemOption(
            label = message("codemodernizer.chat.message.one_or_multiple_diffs_form.one_diff"),
            value = message("codemodernizer.chat.message.one_or_multiple_diffs_form.one_diff"),
        ),
        FormItemOption(
            label = message("codemodernizer.chat.message.one_or_multiple_diffs_form.multiple_diffs"),
            value = message("codemodernizer.chat.message.one_or_multiple_diffs_form.multiple_diffs"),
        )
    )
)

private fun getUserLanguageUpgradeSelectionFormattedMarkdown(moduleName: String, targetJdkVersion: String): String = """
        ### ${message("codemodernizer.chat.prompt.title.details")}
        -------------

        | | |
        | :------------------- | -------: |
        | **${message("codemodernizer.chat.prompt.label.module")}**             |   $moduleName   |
        | **${message("codemodernizer.chat.prompt.label.target_version")}** |  $targetJdkVersion   |
""".trimIndent()

private fun getUserSQLConversionSelectionFormattedMarkdown(moduleName: String, schema: String) = """
        ### ${message("codemodernizer.chat.prompt.title.details")}
        -------------

        | | |
        | :------------------- | -------: |
        | **${message("codemodernizer.chat.prompt.label.module")}**             |   $moduleName   |
        | **Schema** |  $schema   |
""".trimIndent()

private fun getUserHilSelectionMarkdown(dependencyName: String, currentVersion: String, selectedVersion: String): String = """
        ### ${message("codemodernizer.chat.prompt.title.dependency_details")}
        -------------

        | | |
        | :------------------- | -------: |
        | **${message("codemodernizer.chat.prompt.label.dependency_name")}**             |   $dependencyName   |
        | **${message("codemodernizer.chat.prompt.label.dependency_current_version")}**             |   $currentVersion |
        | **${message("codemodernizer.chat.prompt.label.dependency_selected_version")}**             |   $selectedVersion |
""".trimIndent()

fun buildChooseTransformationObjectiveChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.choose_objective"),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildUserReplyChatContent(reply: String) = CodeTransformChatMessageContent(
    message = reply,
    type = CodeTransformChatMessageType.Prompt,
)

fun buildCheckingValidProjectChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.validation.check_eligible_modules"),
    type = CodeTransformChatMessageType.PendingAnswer,
)

fun buildLanguageUpgradeProjectValidChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.validation.check_passed"),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildProjectInvalidChatContent(validationResult: ValidationResult): CodeTransformChatMessageContent {
    val errorMessage = when (validationResult.invalidTelemetryReason.category) {
        CodeTransformPreValidationError.UnsupportedJavaVersion, CodeTransformPreValidationError.UnsupportedBuildSystem ->
            message("codemodernizer.chat.message.validation.error.unsupported_module")
        CodeTransformPreValidationError.RemoteRunProject -> message("codemodernizer.notification.warn.invalid_project.description.reason.remote_backend")
        CodeTransformPreValidationError.NonSsoLogin -> message("codemodernizer.notification.warn.invalid_project.description.reason.not_logged_in")
        CodeTransformPreValidationError.EmptyProject -> message("codemodernizer.notification.warn.invalid_project.description.reason.missing_content_roots")
        CodeTransformPreValidationError.NoJavaProject -> message("codemodernizer.chat.message.validation.error.no_java_project")
        CodeTransformPreValidationError.JavaDowngradeAttempt -> message("codemodernizer.chat.message.validation.error.downgrade_attempt")
        else -> message("codemodernizer.chat.message.validation.error.other")
    }

    return CodeTransformChatMessageContent(
        message = "$errorMessage\n\n${message("codemodernizer.chat.message.validation.error.more_info", CODE_TRANSFORM_PREREQUISITES)}",
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )
}

fun buildStartNewTransformFollowup(): CodeTransformChatMessageContent = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    followUps = listOf(
        startNewTransformFollowUp
    )
)

fun buildUserInputSkipTestsFlagChatIntroContent(): CodeTransformChatMessageContent =
    CodeTransformChatMessageContent(
        message = message("codemodernizer.chat.message.skip_tests"),
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )

fun buildUserInputSkipTestsFlagChatContent(): CodeTransformChatMessageContent =
    CodeTransformChatMessageContent(
        message = message("codemodernizer.chat.form.user_selection.title"),
        buttons = listOf(
            confirmSkipTestsSelectionButton,
            cancelUserSelectionButton,
        ),
        formItems = listOf(selectSkipTestsFlagFormItem),
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )
fun buildUserInputOneOrMultipleDiffsChatIntroContent(version: String): CodeTransformChatMessageContent =
    CodeTransformChatMessageContent(
        message = message("codemodernizer.chat.message.one_or_multiple_diffs", version.substring(4)), // extract "17" / "21" from "JDK_17" / "JDK_21"
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )
fun buildUserInputOneOrMultipleDiffsFlagChatContent(): CodeTransformChatMessageContent =
    CodeTransformChatMessageContent(
        message = message("codemodernizer.chat.form.user_selection.title"),
        buttons = listOf(
            confirmOneOrMultipleDiffsSelectionButton,
            cancelUserSelectionButton,
        ),
        formItems = listOf(selectOneOrMultipleDiffsFlagFormItem),
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )

fun buildUserSkipTestsFlagSelectionChatContent(skipTestsSelection: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.skip_tests_form.response", skipTestsSelection.lowercase())
)

fun buildUserOneOrMultipleDiffsSelectionChatContent(oneOrMultipleDiffsSelection: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.one_or_multiple_diffs_form.response", oneOrMultipleDiffsSelection.lowercase())
)

fun buildUserInputLanguageUpgradeChatContent(project: Project, validationResult: ValidationResult): CodeTransformChatMessageContent {
    val moduleBuildFiles = validationResult.validatedBuildFiles

    return CodeTransformChatMessageContent(
        message = message("codemodernizer.chat.form.user_selection.title"),
        buttons = listOf(
            confirmUserSelectionLanguageUpgradeButton,
            cancelUserSelectionButton,
        ),
        formItems = listOf(
            getSelectModuleFormItem(project, moduleBuildFiles),
            selectTargetVersionFormItem,
        ),
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )
}

fun buildUserInputSQLConversionMetadataChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.form.user_selection.item.choose_sql_metadata_file"),
    buttons = listOf(
        confirmUserSelectionSQLConversionMetadataButton,
        cancelUserSelectionButton,
    ),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildUserInputCustomDependencyVersionsChatContent() = CodeTransformChatMessageContent(
    message = "Optionally, provide a .YAML file which specifies custom dependency versions you want Q to upgrade to.",
    buttons = listOf(
        confirmCustomDependencyVersionsButton,
        continueTransformationButton,
    ),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildPromptTargetJDKNameChatContent(version: String) = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.enter_jdk_name", version),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildInvalidTargetJdkNameChatContent(jdkName: String) = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.enter_jdk_name_error", jdkName),
    type = CodeTransformChatMessageType.FinalizedAnswer,
    followUps = listOf(startNewTransformFollowUp)
)

fun buildCustomDependencyVersionsFileValidChatContent() = CodeTransformChatMessageContent(
    message = "I received your .yaml file and will upload it to Q.",
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildModuleSchemaFormChatContent(project: Project, javaModules: List<VirtualFile>, schemaOptions: Set<String>) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    buttons = listOf(
        confirmUserSelectionSQLConversionModuleSchemaButton,
        cancelUserSelectionButton,
    ),
    formItems = listOf(
        getSelectSQLModuleFormItem(project, javaModules),
        getSelectSQLSchemaFormItem(schemaOptions),
    ),
)

fun buildModuleSchemaFormIntroChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.sql_module_schema_prompt"),
)

fun buildSQLMetadataValidationSuccessIntroChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.sql_metadata_success"),
)

fun buildSQLMetadataValidationSuccessDetailsChatContent(validationResult: SqlMetadataValidationResult) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = """
        ### ${message("codemodernizer.chat.prompt.title.details")}
        -------------

        | | |
        | :------------------- | -------: |
        | **Source DB**             |   ${validationResult.sourceVendor}   |
        | **Target DB**             |   ${validationResult.targetVendor} |
        | **Host**             |   ${validationResult.sourceServerName} |
    """.trimIndent(),
)

fun buildSQLMetadataValidationErrorChatContent(errorReason: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = errorReason,
)

fun buildCustomDependencyVersionsFileInvalidChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = "The .yaml file you uploaded does not follow the format of the sample YAML file provided.",
)

fun buildUserCancelledChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.transform_cancelled_by_user"),
)

fun buildUserStopTransformChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.Prompt,
    message = message("codemodernizer.chat.prompt.stop_transform"),
)

fun buildTransformStoppingChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.transform_stopping"),
)

fun buildTransformStoppedChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.transform_stopped_by_user"),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildTransformFailedChatContent(failureReason: String) = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.transform_failed", failureReason),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildUserSQLConversionSelectionSummaryChatContent(moduleName: String, schema: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.Prompt,
    message = getUserSQLConversionSelectionFormattedMarkdown(moduleName, schema)
)

fun buildUserLanguageUpgradeSelectionSummaryChatContent(moduleName: String, targetJdkVersion: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.Prompt,
    message = getUserLanguageUpgradeSelectionFormattedMarkdown(moduleName, targetJdkVersion)
)

fun buildContinueTransformationChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = "Ok, I will continue without this information.",
)

fun buildCompileLocalInProgressChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.local_build_begin"),
    buttons = listOf(
        openMvnBuildButton,
    ),
)

fun buildCompileLocalFailedChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = "${message(
        "codemodernizer.chat.message.local_build_failed"
    )}\n\n${message(
        "codemodernizer.chat.message.validation.error.more_info",
        CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE
    )}",
)

fun buildCompileLocalFailedNoJdkChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = "${message(
        "codemodernizer.chat.message.validation.no_jdk"
    )}\n\n${message(
        "codemodernizer.chat.message.validation.error.more_info",
        CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE
    )}",
)

fun buildZipUploadFailedChatMessage(failureReason: UploadFailureReason): String {
    val resultMessage = when (failureReason) {
        is UploadFailureReason.PRESIGNED_URL_EXPIRED -> "${message(
            "codemodernizer.chat.message.upload_failed_url_expired"
        )}\n\n${message(
            "codemodernizer.chat.message.validation.error.more_info",
            CODE_TRANSFORM_TROUBLESHOOT_DOC_ALLOW_S3_ACCESS
        )}"

        is UploadFailureReason.HTTP_ERROR -> "${message(
            "codemodernizer.chat.message.upload_failed_http_error",
            failureReason.statusCode
        )}\n\n${message(
            "codemodernizer.chat.message.validation.error.more_info",
            CODE_TRANSFORM_TROUBLESHOOT_DOC_UPLOAD_ERROR_OVERVIEW
        )}"

        is UploadFailureReason.CONNECTION_REFUSED -> message("codemodernizer.chat.message.upload_failed_connection_refused")

        is UploadFailureReason.OTHER -> "${message(
            "codemodernizer.chat.message.upload_failed_other",
            failureReason.errorMessage
        )}\n\n${message(
            "codemodernizer.chat.message.validation.error.more_info",
            CODE_TRANSFORM_TROUBLESHOOT_DOC_UPLOAD_ERROR_OVERVIEW
        )}"

        is UploadFailureReason.CREDENTIALS_EXPIRED -> message("q.connection.expired")

        is UploadFailureReason.SSL_HANDSHAKE_ERROR -> "${message(
            "codemodernizer.chat.message.upload_failed_ssl_error"
        )}\n\n${message(
            "codemodernizer.chat.message.validation.error.more_info",
            CODE_TRANSFORM_TROUBLESHOOT_DOC_CONFIGURE_PROXY
        )}"
    }
    return resultMessage
}

fun buildAbsolutePathWarning(warning: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = warning,
)

fun buildCompileLocalSuccessChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.local_build_success"),
)

fun buildTransformBeginChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.transform_begin"),
)

fun buildTransformInProgressChatContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.transform_in_progress"),
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildTransformResumingChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.resume_ongoing"),
    type = CodeTransformChatMessageType.PendingAnswer,
)

fun buildTransformResultChatContent(result: CodeModernizerJobCompletedResult, totalPatchFiles: Int? = null): CodeTransformChatMessageContent {
    val resultMessage = when (result) {
        is CodeModernizerJobCompletedResult.JobAbortedZipTooLarge -> {
            "${message(
                "codemodernizer.chat.message.result.zip_too_large"
            )}\n\n${message(
                "codemodernizer.chat.message.validation.error.more_info",
                CODE_TRANSFORM_TROUBLESHOOT_DOC_PROJECT_SIZE
            )}"
        }
        is CodeModernizerJobCompletedResult.ZipUploadFailed -> {
            buildZipUploadFailedChatMessage(result.failureReason)
        }
        is CodeModernizerJobCompletedResult.JobCompletedSuccessfully -> {
            if (totalPatchFiles == 1) {
                message("codemodernizer.chat.message.result.success")
            } else {
                message("codemodernizer.chat.message.result.success.multiple_diffs")
            }
        }
        is CodeModernizerJobCompletedResult.JobPartiallySucceeded -> {
            if (totalPatchFiles == 1) {
                message("codemodernizer.chat.message.result.partially_success")
            } else {
                message("codemodernizer.chat.message.result.partially_success.multiple_diffs")
            }
        }
        is CodeModernizerJobCompletedResult.JobFailed -> {
            message("codemodernizer.chat.message.result.fail_with_known_reason", result.failureReason)
        }
        is CodeModernizerJobCompletedResult.JobFailedInitialBuild -> {
            if (result.hasBuildLog) {
                message("codemodernizer.chat.message.result.fail_initial_build")
            } else {
                message("codemodernizer.chat.message.result.fail_initial_build_no_build_log", result.failureReason)
            }
        }
        is CodeModernizerJobCompletedResult.UnableToCreateJob -> {
            result.failureReason
        }
        is CodeModernizerJobCompletedResult.RetryableFailure -> {
            result.failureReason
        }
        else -> {
            message("codemodernizer.chat.message.result.fail")
        }
    }

    return CodeTransformChatMessageContent(
        type = CodeTransformChatMessageType.FinalizedAnswer,
        message = resultMessage,
        buttons = if (result is CodeModernizerJobCompletedResult.JobPartiallySucceeded || result is CodeModernizerJobCompletedResult.JobCompletedSuccessfully) {
            listOf(createViewDiffButton(if (totalPatchFiles == 1) "View diff" else "View diff 1/$totalPatchFiles"), viewSummaryButton)
        } else if (result is CodeModernizerJobCompletedResult.JobFailedInitialBuild && result.hasBuildLog) {
            listOf(viewBuildLog)
        } else {
            null
        },
        followUps = listOf(startNewTransformFollowUp),
    )
}

fun buildTransformAwaitUserInputChatContent(dependency: Dependency): CodeTransformChatMessageContent {
    val majors = (dependency.majors.orEmpty()).sorted()
    val minors = (dependency.minors.orEmpty()).sorted()
    val incrementals = (dependency.incrementals.orEmpty()).sorted()
    val total = majors.size + minors.size + incrementals.size

    var message = message("codemodernizer.chat.message.hil.dependency_summary", total, dependency.currentVersion.orEmpty())

    if (majors.isNotEmpty()) {
        message += message("codemodernizer.chat.message.hil.dependency_latest_major", majors.last())
    }
    if (minors.isNotEmpty()) {
        message += message("codemodernizer.chat.message.hil.dependency_latest_minor", minors.last())
    }
    if (incrementals.isNotEmpty()) {
        message += message("codemodernizer.chat.message.hil.dependency_latest_incremental", incrementals.last())
    }

    return CodeTransformChatMessageContent(
        type = CodeTransformChatMessageType.FinalizedAnswer,
        message = message,
        formItems = listOf(
            FormItem(
                id = CodeTransformFormItemId.DependencyVersion.id,
                title = message("codemodernizer.chat.message.hil.dependency_choose_version"),
                options = (majors + minors + incrementals).map { FormItemOption(it, it) },
            )
        ),
        buttons = listOf(
            confirmHilSelectionButton,
            rejectHilSelectionButton,
        ),
    )
}

fun buildTransformDependencyErrorChatContent(
    hilDownloadArtifact: CodeTransformHilDownloadArtifact,
    showButton: Boolean = true,
) = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.hil.pom_snippet_title") +
        "\n\n```xml" +
        "\n" +
        "<dependencies>\n" +
        "  <dependency>\n" +
        "    <groupId>${hilDownloadArtifact.manifest.pomGroupId}</groupId>\n" +
        "    <artifactId>${hilDownloadArtifact.manifest.pomArtifactId}</artifactId>\n" +
        "    <version>${hilDownloadArtifact.manifest.sourcePomVersion}</version>\n" +
        "  </dependency>\n" +
        "</dependencies>",
    type = CodeTransformChatMessageType.PendingAnswer,
    buttons = if (showButton) {
        listOf(openDependencyErrorPomFileButton)
    } else {
        emptyList()
    },

)

fun buildTransformFindingLocalAlternativeDependencyChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.hil.searching"),
    type = CodeTransformChatMessageType.PendingAnswer,
)

fun buildUserHilSelection(dependencyName: String, currentVersion: String, selectedVersion: String) = CodeTransformChatMessageContent(
    message = getUserHilSelectionMarkdown(dependencyName, currentVersion, selectedVersion),
    type = CodeTransformChatMessageType.Prompt,
)

fun buildCompileHilAlternativeVersionContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.hil.trying_resume"),
)

fun buildHilResumedContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.hil.resumed"),
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildHilRejectContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.hil.user_rejected"),
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildHilInitialContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.hil.start_message"),
)

fun buildHilErrorContent(errorMessage: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = errorMessage,
)

fun buildHilResumeWithErrorContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = message("codemodernizer.chat.message.hil.continue_after_error"),
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildHilCannotResumeContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = message("codemodernizer.chat.message.hil.cannot_resume"),
    followUps = listOf(
        startNewTransformFollowUp
    ),
)

fun buildDownloadFailureChatContent(downloadFailureReason: DownloadFailureReason): CodeTransformChatMessageContent? {
    val artifactText = getDownloadedArtifactTextFromType(downloadFailureReason.artifactType)
    val (message, docLink) = when (downloadFailureReason) {
        is DownloadFailureReason.SSL_HANDSHAKE_ERROR -> Pair(
            message("codemodernizer.chat.message.download_failed_ssl", artifactText),
            CODE_TRANSFORM_TROUBLESHOOT_DOC_CONFIGURE_PROXY,
        )

        is DownloadFailureReason.PROXY_WILDCARD_ERROR -> Pair(
            message("codemodernizer.chat.message.download_failed_wildcard", artifactText),
            CODE_TRANSFORM_TROUBLESHOOT_DOC_REMOVE_WILDCARD,
        )

        is DownloadFailureReason.OTHER -> Pair(
            message("codemodernizer.chat.message.download_failed_other", artifactText, downloadFailureReason.errorMessage),
            CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_ERROR_OVERVIEW,
        )
        is DownloadFailureReason.CREDENTIALS_EXPIRED -> return null // credential expiry resets chat, no point emitting a message
        is DownloadFailureReason.INVALID_ARTIFACT ->
            if (downloadFailureReason.artifactType == TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS) {
                Pair(
                    message("codemodernizer.chat.message.download_failed_client_instructions_expired"),
                    CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_EXPIRED,
                )
            } else {
                Pair(
                    message("codemodernizer.chat.message.download_failed_invalid_artifact", artifactText),
                    CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_EXPIRED,
                )
            }
    }

    // DownloadFailureReason.OTHER might be retryable, so including buttons to allow retry.
    return CodeTransformChatMessageContent(
        type = CodeTransformChatMessageType.FinalizedAnswer,
        message = "$message\n\n${message("codemodernizer.chat.message.validation.error.more_info", docLink)}",
        buttons = if (downloadFailureReason.artifactType == TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS &&
            (downloadFailureReason is DownloadFailureReason.OTHER || downloadFailureReason is DownloadFailureReason.SSL_HANDSHAKE_ERROR)
        ) {
            listOf(viewDiffButton, viewSummaryButton)
        } else {
            null
        },
    )
}

fun getDownloadedArtifactTextFromType(artifactType: TransformationDownloadArtifactType): String =
    when (artifactType) {
        TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS -> "upgraded code"
        TransformationDownloadArtifactType.LOGS -> "build log"
        TransformationDownloadArtifactType.UNKNOWN_TO_SDK_VERSION -> "code"
        TransformationDownloadArtifactType.GENERATED_CODE -> "code"
    }
