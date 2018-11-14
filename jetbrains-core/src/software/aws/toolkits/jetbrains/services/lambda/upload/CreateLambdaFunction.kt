// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.cloudformation.CloudFormationTemplateIndex
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerResolver
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

class ConditionalCreateLambdaFunction(
    private val handlerName: String,
    private val element: PsiElement,
    private val lambdaHandlerResolver: LambdaHandlerResolver
) : CreateLambdaFunction() {
    override fun update(e: AnActionEvent?) {
        super.update(e)

        val templateFunctionHandlers = CloudFormationTemplateIndex.listFunctions(element.project)
            .mapNotNull { it.handler() }
            .distinct()

        val allowAction = lambdaHandlerResolver.determineHandlers(element, element.containingFile.virtualFile)
            .none { elementHandler ->
                templateFunctionHandlers.any { templateHandler -> templateHandler == elementHandler }
            }

        e?.presentation?.isVisible = allowAction
    }

    override fun getEditFunctionDialog(project: Project, runtime: Runtime?): EditFunctionDialog =
        EditFunctionDialog(project = project, mode = NEW, runtime = runtime, handlerName = handlerName)
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