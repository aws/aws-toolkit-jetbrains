// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvalidParameterValueException
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.awt.Component
import javax.swing.JComponent

class ConfigureLambdaDialog(
    private val project: Project,
    private val sqsClient: SqsClient,
    private val queue: Queue
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("ConfigureLambda") {
    private val lambdaClient: LambdaClient = project.awsClient()
    val view = ConfigureLambdaPanel(project)

    init {
        title = message("sqs.configure.lambda")

        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.lambdaFunction

    override fun doValidate(): ValidationInfo? = validateFields()

    override fun doOKAction() {
        if (isOKActionEnabled) {
            isOKActionEnabled = false

            if (validateArn(functionSelected())) {
                launch {
                    try {
                        configureLambda()
                        runInEdt(ModalityState.any()) {
                            close(OK_EXIT_CODE)
                        }
                    } catch (e: InvalidParameterValueException) {
                        // Exception thrown for invalid permission
                        println("INVALID PERMISSION")
                        isOKActionEnabled = true
                    } catch (e: Exception) {
                        LOG.warn(e) { message("sqs.configure.lambda.error", queue.queueName, functionSelected()) }
                        setErrorText(e.message)
                        isOKActionEnabled = true
                    }
                }
            } else {
                setErrorText(message("sqs.configure.lambda.validation.function.arn"))
                isOKActionEnabled = true
            }
        }
    }

    private fun validateFields(): ValidationInfo? {
        if (view.listButton.isSelected) {
            view.lambdaFunction.selected() ?: return ValidationInfo(message("sqs.configure.lambda.validation.function"), view.lambdaFunction)
        } else {
            if (functionSelected().isEmpty()) {
                return ValidationInfo(message("sqs.configure.lambda.validation.function"), view.functionArn)
            }
        }
        return null
    }

    fun configureLambda() {
        lambdaClient.createEventSourceMapping {
            it.functionName(functionSelected())
            it.eventSourceArn(queue.arn)
        }
    }

    fun functionSelected(): String {
        return if (view.listButton.isSelected) {
            view.lambdaFunction.selected() as String
        } else {
            view.functionArn.text
        }
    }

    private fun validateArn(arn: String): Boolean {
        return try {
            lambdaClient.getFunction { it.functionName(arn) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        val LOG = getLogger<ConfigureLambdaDialog>()
    }
}
