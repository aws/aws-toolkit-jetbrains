// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.ContextEntry
import software.amazon.awssdk.services.iam.model.ContextKeyTypeEnum
import software.amazon.awssdk.services.iam.model.PolicyEvaluationDecisionType
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.SqsTelemetry
import javax.swing.JComponent

class SubscribeSnsDialog(
    private val project: Project,
    private val queue: Queue
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("SubscribeSnsDialog") {
    private val snsClient: SnsClient = project.awsClient()
    private val sqsClient: SqsClient = project.awsClient()
    private val iamClient: IamClient = project.awsClient()

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
            return ValidationInfo(message("sqs.subscribe.sns.validation.empty_topic"), view.topicSelector)
        }
        return null
    }

    override fun doCancelAction() {
        SqsTelemetry.subscribeSns(project, Result.Cancelled, queue.telemetryType())
        super.doCancelAction()
    }

    override fun doOKAction() {
        if (!isOKActionEnabled) {
            return
        }
        setOKButtonText(message("sqs.subscribe.sns.in_progress"))
        isOKActionEnabled = false

        launch {
            try {
                val topicArn = topicSelected()

                val policy = sqsClient.getQueueAttributes {
                    it.queueUrl(queue.queueUrl)
                    it.attributeNames(QueueAttributeName.POLICY)
                }.attributes()[QueueAttributeName.POLICY]

                try {
                    if (determineIfNeedPolicy(policy)) {
                        // DO NOT change to withCoroutineUiContext, it breaks the panel with the wrong state
                        runInEdt(ModalityState.any()) {
                            if (!ConfirmQueuePolicyDialog(
                                    project,
                                    sqsClient,
                                    queue,
                                    topicSelected(),
                                    policy,
                                    view.component
                                ).showAndGet()
                            ) {
                                close(CANCEL_EXIT_CODE)
                            } else {
                                continueSubscribing()
                            }
                        }
                    } else {
                        continueSubscribing()
                    }
                } catch (e: Exception) {
                    // give warning that we don't know
                }
            } catch (e: Exception) {
                LOG.warn(e) { message("sqs.subscribe.sns.failed", queue.queueName, topicSelected()) }
                setErrorText(e.message)
                setOKButtonText(message("sqs.subscribe.sns.subscribe"))
                isOKActionEnabled = true
                SqsTelemetry.subscribeSns(project, Result.Failed, queue.telemetryType())
            }
        }
    }

    private fun continueSubscribing() {
        subscribe(topicSelected())
        runInEdt(ModalityState.any()) {
            close(OK_EXIT_CODE)
        }
        notifyInfo(message("sqs.service_name"), message("sqs.subscribe.sns.success", topicSelected()), project)
        SqsTelemetry.subscribeSns(project, Result.Succeeded, queue.telemetryType())
    }

    private fun topicSelected(): String = view.topicSelector.selected()?.topicArn() ?: ""

    internal fun subscribe(arn: String) {
        snsClient.subscribe {
            it.topicArn(arn)
            it.protocol(PROTOCOL)
            it.endpoint(queue.arn)
        }
    }

    private fun determineIfNeedPolicy(existingPolicy: String?): Boolean {
        if (existingPolicy == null) {
            return true
        }

        val allowed = iamClient.simulateCustomPolicy {
            it.contextEntries(
                ContextEntry.builder()
                    .contextKeyType(ContextKeyTypeEnum.STRING)
                    .contextKeyName("aws:SourceArn")
                    .contextKeyValues(topicSelected())
                    .build()
            )
            it.actionNames("sqs:SendMessage")
            it.resourceArns(queue.arn)
            it.policyInputList(existingPolicy)
        }.evaluationResults().first()

        if (allowed.evalDecision() != PolicyEvaluationDecisionType.ALLOWED) {
            return true
        }

        return false
    }

    private companion object {
        val LOG = getLogger<SubscribeSnsDialog>()
        val mapper = jacksonObjectMapper()
        const val PROTOCOL = "sqs"
    }
}
