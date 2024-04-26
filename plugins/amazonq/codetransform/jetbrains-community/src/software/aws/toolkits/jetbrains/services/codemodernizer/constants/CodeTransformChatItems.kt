// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.constants

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.amazonq.CODE_TRANSFORM_TROUBLESHOOT_DOC
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
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ValidationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getModuleOrProjectNameForFile
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.FollowUpType
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import software.aws.toolkits.resources.AwsToolkitBundle.message
import software.aws.toolkits.telemetry.CodeTransformPreValidationError

private val cancelUserSelectionButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = false,
    text = message("codemodernizer.chat.message.button.cancel"),
    id = CodeTransformButtonId.CancelTransformation.id,
)

private val confirmUserSelectionButton = Button(
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
    text = message("codemodernizer.chat.message.button.confirm"),
    id = CodeTransformButtonId.StartTransformation.id,
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

private val viewSummaryButton = Button(
    id = CodeTransformButtonId.ViewSummary.id,
    text = message("codemodernizer.chat.message.button.view_summary"),
    keepCardAfterClick = true,
)

private val confirmHilSelectionButton = Button(
    id = CodeTransformButtonId.ConfirmHilSelection.id,
    text = "Submit",
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
)

private val rejectHilSelectionButton = Button(
    id = CodeTransformButtonId.RejectHilSelection.id,
    text = "Continue without selection",
    keepCardAfterClick = false,
    waitMandatoryFormItems = true,
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

private val selectTargetVersionFormItem = FormItem(
    id = CodeTransformFormItemId.SelectTargetVersion.id,
    title = message("codemodernizer.chat.form.user_selection.item.choose_target_version"),
    mandatory = true,
    options = listOf(
        FormItemOption(
            label = "JDK17",
            value = "17",
        )
    )
)

private fun getUserSelectionFormattedMarkdown(moduleName: String): String = """
        ### ${message("codemodernizer.chat.prompt.title.details")}
        -------------

        | | |
        | :------------------- | -------: |
        | **${message("codemodernizer.chat.prompt.label.module")}**             |   $moduleName   |
        | **${message("codemodernizer.chat.prompt.label.target_version")}** |  JDK17   |
""".trimIndent()

fun buildCheckingValidProjectChatContent() = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.validation.check_eligible_projects"),
    type = CodeTransformChatMessageType.PendingAnswer,
)

fun buildProjectValidChatContent(validationResult: ValidationResult) = CodeTransformChatMessageContent(
    message = message("codemodernizer.chat.message.validation.check_passed", validationResult.validatedProjectJdkName),
    type = CodeTransformChatMessageType.FinalizedAnswer,
)
fun buildProjectInvalidChatContent(validationResult: ValidationResult): CodeTransformChatMessageContent {
    val errorMessage = when (validationResult.invalidTelemetryReason.category) {
        CodeTransformPreValidationError.NoPom -> message("codemodernizer.chat.message.validation.error.no_pom")
        CodeTransformPreValidationError.UnsupportedJavaVersion -> message("codemodernizer.chat.message.validation.error.unsupported_java_version")
        else -> message("codemodernizer.chat.message.validation.error.other")
    }

    return CodeTransformChatMessageContent(
        message = "$errorMessage\n\n${message("codemodernizer.chat.message.validation.error.more_info", CODE_TRANSFORM_TROUBLESHOOT_DOC)}",
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )
}

fun buildStartNewTransformFollowup(): CodeTransformChatMessageContent = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    followUps = listOf(
        startNewTransformFollowUp
    )
)

fun buildUserInputChatContent(project: Project, validationResult: ValidationResult): CodeTransformChatMessageContent {
    val moduleBuildFiles = validationResult.validatedBuildFiles

    return CodeTransformChatMessageContent(
        message = message("codemodernizer.chat.form.user_selection.title"),
        buttons = listOf(
            confirmUserSelectionButton,
            cancelUserSelectionButton,
        ),
        formItems = listOf(
            getSelectModuleFormItem(project, moduleBuildFiles),
            selectTargetVersionFormItem,
        ),
        type = CodeTransformChatMessageType.FinalizedAnswer,
    )
}

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

fun buildUserSelectionSummaryChatContent(moduleName: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.Prompt,
    message = getUserSelectionFormattedMarkdown(moduleName)
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
        CODE_TRANSFORM_TROUBLESHOOT_DOC
    )}",
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

fun buildTransformResultChatContent(result: CodeModernizerJobCompletedResult): CodeTransformChatMessageContent {
    val resultMessage = when (result) {
        is CodeModernizerJobCompletedResult.JobAbortedZipTooLarge -> {
            "${message(
                "codemodernizer.chat.message.result.zip_too_large"
            )}\n\n${message(
                "codemodernizer.chat.message.validation.error.more_info",
                CODE_TRANSFORM_TROUBLESHOOT_DOC
            )}"
        }
        is CodeModernizerJobCompletedResult.JobCompletedSuccessfully -> {
            message("codemodernizer.chat.message.result.success")
        }
        is CodeModernizerJobCompletedResult.JobPartiallySucceeded -> {
            message("codemodernizer.chat.message.result.partially_success")
        }
        else -> {
            message("codemodernizer.chat.message.result.fail")
        }
    }

    return CodeTransformChatMessageContent(
        type = CodeTransformChatMessageType.FinalizedAnswer,
        message = resultMessage,
        buttons = if (result is CodeModernizerJobCompletedResult.JobPartiallySucceeded || result is CodeModernizerJobCompletedResult.JobCompletedSuccessfully) {
            listOf(viewDiffButton, viewSummaryButton)
        } else {
            null
        },
    )
}

// TODO translate
fun buildTransformAwaitUserInputChatContent(dependency: Dependency): CodeTransformChatMessageContent {
    val majors = (dependency.majors ?: listOf()).sorted()
    val minors = (dependency.minors ?: listOf()).sorted()
    val incrementals = (dependency.incrementals ?: listOf()).sorted()
    val total = majors.size + minors.size + incrementals.size

    var message = "I found $total other dependency versions that are more recent than the dependency in your code that's causing an error (${dependency.currentVersion})."
    if (majors.isNotEmpty()) {
        message += "\n\nLatest major version: ${majors.last()}"
    }
    if (minors.isNotEmpty()) {
        message += "\n\nLatest minor version: ${minors.last()}"
    }
    if (incrementals.isNotEmpty()) {
        message += "\n\nLatest incremental version: ${incrementals.last()}"
    }

    return CodeTransformChatMessageContent(
        type = CodeTransformChatMessageType.FinalizedAnswer,
        message = message,
        formItems = listOf(
            FormItem(
                id = CodeTransformFormItemId.DependencyVersion.id,
                title = "I can replace this dependency with a newer version. Choose which version I should use:",
                options = (majors + minors + incrementals).map { FormItemOption(it, it) },
            )
        ),
        buttons = listOf(
            confirmHilSelectionButton,
            rejectHilSelectionButton,
        ),
    )
}

fun buildTransformDependencyErrorChatContent(hilDownloadArtifact: CodeTransformHilDownloadArtifact) = CodeTransformChatMessageContent(
    // TODO string review
    message = "Here is the dependency causing the issue:" +
        "\n\n```xml" +
        "\n" +
        "<dependencies>\n" +
        "  <dependency>\n" +
        "    <groupId>${hilDownloadArtifact.manifest.pomGroupId}</groupId>\n" +
        "    <artifactId>${hilDownloadArtifact.manifest.pomArtifactId}</artifactId>\n" +
        "    <version>${hilDownloadArtifact.manifest.sourcePomVersion}</version>\n" +
        "  </dependency>\n" +
        "</dependencies>",
    type = CodeTransformChatMessageType.FinalizedAnswer,
)

fun buildTransformFindingLocalAlternativeDependencyChatContent() = CodeTransformChatMessageContent(
    // TODO string review
    message = "I’m searching for other dependency versions available in your Maven repository...",
    type = CodeTransformChatMessageType.PendingAnswer,
)

fun buildUserHilSelection(version: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.Prompt,
    message = "Update using version $version."
)

fun buildCompileHilAlternativeVersionContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = "Trying to resume transformation with your selected version.",
)

fun buildHilResumedContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    // TODO complete message
    message = "I received your target version dependency. I'll continue transforming your code. You can monitor progress in the Transformation Hub.",
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildHilRejectContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = "I'll continue upgrading your module. When I'm done, you can review the dependency error in the Transformation summary.",
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildHilInitialContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = "I was not able to upgrade all dependencies. To resolve it, I'll try to find an updated dependency in your local Maven repository. I'll need additional information from you to continue.",
)

fun buildHilErrorContent(errorMessage: String) = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = errorMessage,
)

fun buildHilResumeWithErrorContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.PendingAnswer,
    message = "I'll continue upgrading your module. When I'm done, you can review the dependency error in the Transformation summary.",
    buttons = listOf(
        openTransformHubButton,
        stopTransformButton,
    ),
)

fun buildHilCannotResumeContent() = CodeTransformChatMessageContent(
    type = CodeTransformChatMessageType.FinalizedAnswer,
    message = "I ran into an issue trying to resume your transformation.",
    followUps = listOf(
        startNewTransformFollowUp
    ),
)
