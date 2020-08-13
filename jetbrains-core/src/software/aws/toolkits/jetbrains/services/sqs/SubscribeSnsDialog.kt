// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import software.aws.toolkits.jetbrains.services.sqs.resources.SqsResources
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import javax.swing.JComponent

private val NOTIFICATION_TITLE = message("sqs.service_name")

class SubscribeSnsDialog(
    private val project: Project,
    private val queue: Queue
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("SubscribeSnsDialog") {
    private val snsClient: SnsClient = project.awsClient()
    val view = SubscribeSnsPanel(project)

    init {
        title = message("sqs.subscribe.sns")
        setOKButtonText(message("sqs.subscribe.sns.subscribe"))

        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.topicSelector

    override fun doValidate(): ValidationInfo? {
        if (topicSelected().isEmpty()) {
            return ValidationInfo(message("sqs.subscribe.sns.validation.empty_topic"), view.topicArn)
        }
        if (!ARN_REGEX.matches(topicSelected())) {
            return ValidationInfo(message("sqs.subscribe.sns.validation.invalid_arn"), view.topicArn)
        }
        return null
    }

    override fun doOKAction() {
        if (isOKActionEnabled) {
            setOKButtonText(message("sqs.subscribe.sns.in_progress"))
            isOKActionEnabled = false

            launch {
                try {
                    subscribe()
                    runInEdt(ModalityState.any()) {
                        close(OK_EXIT_CODE)
                    }
                    project.refreshAwsTree(SqsResources.LIST_QUEUE_URLS)
                    // TODO: maybe display that it is subscribed with subscription arn
                    notifyInfo(NOTIFICATION_TITLE, message("sqs.subscribe.sns.success", topicSelected()), project)
                } catch (e: Exception) {
                    if (containsStatusCode(e.message, message("sqs.subscribe.sns.status.invalid.permission"))) {
                        setErrorText(message("sqs.subscribe.sns.permission.error.text", topicSelected()))
                    } else {
                        setErrorText(e.message)
                    }
                    LOG.warn(e) { message("sqs.subscribe.sns.failed", queue.queueName, topicSelected()) }
                    setOKButtonText(message("sqs.subscribe.sns.subscribe"))
                    isOKActionEnabled = true
                }
            }
        }
    }

    private fun topicSelected(): String = view.topicArn.text

    private fun containsStatusCode(message: String?, code: String): Boolean = message?.contains(code) ?: false

    private fun subscribe() {
        snsClient.subscribe {
            it.topicArn(topicSelected())
            it.protocol(message("sqs.subscribe.sns.protocol"))
            it.endpoint(queue.arn)
        }
    }

    private companion object {
        val LOG = getLogger<SubscribeSnsDialog>()
        val ARN_REGEX = "arn:.+:sns:.+:.+:(.+)".toRegex()
    }
}
