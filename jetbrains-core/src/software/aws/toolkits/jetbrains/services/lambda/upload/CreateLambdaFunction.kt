// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.cloudformation.CloudFormationTemplateIndex
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.lambda.runtime
import software.aws.toolkits.jetbrains.services.lambda.upload.EditFunctionMode.NEW
import software.aws.toolkits.resources.message

open class CreateLambdaFunction() : AnAction(message("lambda.create_new"), null, AwsIcons.Actions.LAMBDA_FUNCTION_NEW) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getRequiredData(LangDataKeys.PROJECT)
        val runtime = event.runtime()

        val dialog = getEditFunctionDialog(project, runtime)

        dialog.show()
    }

    protected open fun getEditFunctionDialog(project: Project, runtime: Runtime?): EditFunctionDialog =
        EditFunctionDialog(project = project, mode = NEW, runtime = runtime)
}

open class CreateElementBasedLambdaFunction(private val handlerName: String, private val project: Project)
    : CreateLambdaFunction() {
    protected var handlerSearch: String = handlerName
    protected var isExactMatch: Boolean = false

    override fun update(e: AnActionEvent?) {
        super.update(e)

        val allowAction = CloudFormationTemplateIndex.listFunctions(project)
            .mapNotNull { it.handler() }
            .none {
                if (isExactMatch) {
                    it == handlerSearch
                } else {
                    it.startsWith(handlerSearch)
                }
            }

        e?.presentation?.isEnabled = allowAction
        e?.presentation?.isVisible = allowAction
    }

    override fun getEditFunctionDialog(project: Project, runtime: Runtime?): EditFunctionDialog =
        EditFunctionDialog(project = project, mode = NEW, runtime = runtime, handlerName = handlerName)
}

class CreateLambdaFunctionFromJavaClass(private val handlerName: String, private val project: Project)
    : CreateElementBasedLambdaFunction(handlerName, project) {

    init {
        handlerSearch = "$handlerName::"
        isExactMatch = false
    }
}

class CreateLambdaFunctionFromJavaMethod(private val handlerName: String, private val project: Project)
    : CreateElementBasedLambdaFunction(handlerName, project) {

    init {
        handlerSearch = handlerName
        isExactMatch = true
    }
}

class CreateLambdaFunctionFromPythonMethod(private val handlerName: String, private val project: Project)
    : CreateElementBasedLambdaFunction(handlerName, project) {

    init {
        handlerSearch = handlerName
        isExactMatch = true
    }
}

data class FunctionUploadDetails(
    val name: String,
    val handler: String,
    val iamRole: IamRole,
    val runtime: Runtime,
    val description: String?,
    val envVars: Map<String, String>,
    val timeout: Int,
    val memorySize: Int
)