// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications.Bus.notify
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.showYesNoDialog
import com.intellij.ui.ScrollPaneFactory
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesRequest
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.TagFilter
import software.aws.toolkits.jetbrains.components.telemetry.AnActionWrapper
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.SettingsSelectorAction
import software.aws.toolkits.jetbrains.settings.AwsSettingsConfigurable
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JTextArea

const val GROUP_DISPLAY_ID = "AWS Toolkit"

fun Exception.notifyError(title: String = "", project: Project? = null) =
    notify(
        Notification(
            GROUP_DISPLAY_ID,
            title,
            this.message ?: "${this::class.java.name}${this.stackTrace?.joinToString("\n", prefix = "\n")}",
            NotificationType.ERROR
        ), project
    )

fun notify(type: NotificationType, title: String, content: String = "", project: Project? = null, notificationActions: Collection<AnAction>) {
    val notification = Notification(GROUP_DISPLAY_ID, title, content, type)

    notificationActions.forEach {
        notification.addAction(it)
    }

    notify(notification, project)
}

fun notifyInfo(title: String, content: String = "", project: Project? = null, listener: NotificationListener? = null) =
    notify(Notification(GROUP_DISPLAY_ID, title, content, NotificationType.INFORMATION, listener), project)

fun notifyInfo(title: String, content: String = "", project: Project? = null, notificationActions: Collection<AnAction>) =
    notify(NotificationType.INFORMATION, title, content, project, notificationActions)

fun notifyWarn(title: String, content: String = "", project: Project? = null, notificationActions: Collection<AnAction>) =
    notify(NotificationType.WARNING, title, content, project, notificationActions)

fun notifyWarn(title: String, content: String = "", project: Project? = null, listener: NotificationListener? = null) =
    notify(Notification(GROUP_DISPLAY_ID, title, content, NotificationType.WARNING, listener), project)

fun notifyError(title: String, content: String = "", project: Project? = null, action: AnAction) =
    notify(Notification(GROUP_DISPLAY_ID, title, content, NotificationType.ERROR).addAction(action), project)

fun notifyError(title: String = message("aws.notification.title"), content: String = "", project: Project? = null, listener: NotificationListener? = null) =
    notify(Notification(GROUP_DISPLAY_ID, title, content, NotificationType.ERROR, listener), project)

/**
 * Notify error that AWS credentials are not configured.
 */
fun notifyNoActiveCredentialsError(
    project: Project,
    title: String = message("aws.notification.title"),
    content: String = message("aws.notification.credentials_missing")
) {
    notifyError(
        title = title,
        content = content,
        project = project,
        action = SettingsSelectorAction(showRegions = false)
    )
}

/**
 * Notify error that AWS SAM CLI is not valid.
 */
fun notifySamCliNotValidError(
    project: Project,
    title: String = message("aws.notification.title"),
    content: String
) {
    notifyError(
        title = title,
        content = message("aws.notification.sam_cli_not_valid", content),
        project = project,
        listener = NotificationListener { notification, _ ->
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AwsSettingsConfigurable::class.java)
            notification.expire()
        }
    )
}

fun <T> tryNotify(message: String, block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    e.notifyError(message)
    null
}

/**
 * Creates a Notification Action that will expire a notification after performing some AnAction
 */
fun createNotificationExpiringAction(action: AnAction): NotificationAction = NotificationAction.create(
    action.templatePresentation.text
) { actionEvent, notification ->
    action.actionPerformed(actionEvent)
    notification.expire()
}

fun createShowMoreInfoDialogAction(actionName: String?, title: String?, message: String?, moreInfo: String?) =
    object : AnActionWrapper(actionName) {
        override fun isDumbAware() = true

        override fun doActionPerformed(e: AnActionEvent) {
            val dialogTitle = title ?: ""

            val textArea = JTextArea(moreInfo).apply {
                columns = 50
                rows = 5
                lineWrap = true
                wrapStyleWord = true
                isEditable = false
            }

            val dialogBuilder = DialogBuilder().apply {
                setTitle(dialogTitle)
                setNorthPanel(JLabel(message))
                setCenterPanel(ScrollPaneFactory.createScrollPane(textArea))
                setPreferredFocusComponent(textArea)
                removeAllActions()
                addOkAction()
            }

            dialogBuilder.show()
        }
    }

private const val CODEPIPELINE_SYSTEM_TAG_KEY = "aws1:codepipeline:pipelineArn"
private const val LAMBDA_RESOURCE_TYPE_FILTER = "lambda:function"
private const val STACK_RESOURCE_TYPE_FILTER = "cloudformation:stack"

fun warnLambdaUpdateAgainstCodePipeline(project: Project, functionName: String, functionArn: String, operation: String): Boolean {
    val codePipelineArn = getCodePipelineArn(project, functionArn, LAMBDA_RESOURCE_TYPE_FILTER)

    return warnResourceUpdateAgainstCodePipeline(project, codePipelineArn, functionName, message("codepipeline.lambda.resource_type"), operation)
}

fun warnStackUpdateAgainstCodePipeline(project: Project, stackName: String, stackArn: String, operation: String): Boolean {
    val codePipelineArn = getCodePipelineArn(project, stackArn, STACK_RESOURCE_TYPE_FILTER)

    return warnResourceUpdateAgainstCodePipeline(project, codePipelineArn, stackName, message("codepipeline.stack.resource_type"), operation)
}

private fun getCodePipelineArn(project: Project, resourceArn: String, resourceTypeFilter: String): String {
    val client: ResourceGroupsTaggingApiClient = AwsClientManager.getInstance(project).getClient()

    val tagFilter = TagFilter.builder().key(CODEPIPELINE_SYSTEM_TAG_KEY).build()
    val request = GetResourcesRequest.builder().tagFilters(tagFilter).resourceTypeFilters(resourceTypeFilter).build()
    val response = client.getResources(request)

    for (resourceTagMapping in response.resourceTagMappingList()) {
        if (resourceTagMapping.resourceARN().equals(resourceArn)) {
            val tag = resourceTagMapping.tags().find { it.key().equals(CODEPIPELINE_SYSTEM_TAG_KEY) }
            if (tag != null) {
                return tag.value()
            }
        }
    }
    return ""
}

private fun warnResourceUpdateAgainstCodePipeline(project: Project, codePipelineArn: String, resourceName: String, resourceType: String, operation: String): Boolean {
    if (!codePipelineArn.isEmpty()) {
        val title = message("codepipeline.resource.update.warning.title")
        val message = message("codepipeline.resource.update.warning.message", resourceType, resourceName, codePipelineArn, operation)
        val yesText = message("codepipeline.resource.update.warning.yes_text")
        val noText = message("codepipeline.resource.update.warning.no_text")

        return showYesNoDialog(title, message, project, yesText, noText, getWarningIcon())
    }

    return false
}
