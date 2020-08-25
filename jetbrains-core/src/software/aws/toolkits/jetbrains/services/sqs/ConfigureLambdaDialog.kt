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
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvalidParameterValueException
import software.aws.toolkits.core.utils.Waiters.waitUntilBlocking
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import java.time.Duration
import javax.swing.JComponent

class ConfigureLambdaDialog(
    private val project: Project,
    private val queue: Queue
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("ConfigureLambda") {
    private val lambdaClient: LambdaClient = project.awsClient()
    private val iamClient: IamClient = project.awsClient()
    val view = ConfigureLambdaPanel(project)
    var policyNeeded = false

    init {
        title = message("sqs.configure.lambda")
        setOKButtonText(message("sqs.configure.lambda.configure"))
        setOKButtonTooltip(message("sqs.configure.lambda.configure.tooltip"))

        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.lambdaFunction

    override fun doValidate(): ValidationInfo? = validateFields()

    override fun doOKAction() {
        if (isOKActionEnabled) {
            isOKActionEnabled = false
            setOKButtonText(message("sqs.configure.lambda.in_progress"))

            launch {
                try {
                    configureLambda(functionSelected())
                    runInEdt(ModalityState.any()) {
                        close(OK_EXIT_CODE)
                    }
                } catch (e: InvalidParameterValueException) {
                    // Exception thrown for invalid permission
                    runInEdt(ModalityState.any()) {
                        val iamDialog = ConfirmIamPolicyDialog(project, iamClient, lambdaClient, functionSelected(), view.component)
                        if (iamDialog.showAndGet()) {
                            waitUntilConfigured(functionSelected())
                            notifyInfo(message("sqs.service_name"), message("sqs.configure.lambda.success", functionSelected()), project)
                            close(OK_EXIT_CODE)
                        } else {
                            setOKButtonText(message("sqs.configure.lambda.configure"))
                            isOKActionEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn(e) { message("sqs.configure.lambda.error", queue.queueName, functionSelected()) }
                    setErrorText(e.message)
                    setOKButtonText(message("sqs.configure.lambda.configure"))
                    isOKActionEnabled = true
                }
            }
        }
    }

    private fun functionSelected(): String = view.lambdaFunction.selected() ?: ""

    private fun validateFields(): ValidationInfo? {
        if (functionSelected().isEmpty()) {
            return ValidationInfo(message("sqs.configure.lambda.validation.function"), view.lambdaFunction)
        }
        return null
    }

    internal fun configureLambda(functionName: String) {
        lambdaClient.createEventSourceMapping {
            it.functionName(functionName)
            it.eventSourceArn(queue.arn)
        }
    }

    private fun waitUntilConfigured(functionName: String) {
        waitUntilBlocking(
            succeedOn = {
                it.eventSourceArn().isNotEmpty()
            },
            exceptionsToIgnore = setOf(InvalidParameterValueException::class),
            maxDuration = Duration.ofSeconds(10),
            call = {
                lambdaClient.createEventSourceMapping {
                    it.functionName(functionName)
                    it.eventSourceArn(queue.arn)
                }
            }
        )
    }

    private companion object {
        val LOG = getLogger<ConfigureLambdaDialog>()
    }
}
