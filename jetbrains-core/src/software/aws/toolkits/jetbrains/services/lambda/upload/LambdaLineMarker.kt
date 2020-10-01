// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.LineMarkerActionWrapper
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.getResource
import software.aws.toolkits.jetbrains.core.getResourceIfPresent
import software.aws.toolkits.jetbrains.services.cloudformation.CloudFormationTemplateIndex.Companion.listFunctions
import software.aws.toolkits.jetbrains.services.lambda.LambdaBuilder
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerResolver
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import software.aws.toolkits.jetbrains.services.lambda.resources.LambdaResources
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import software.aws.toolkits.jetbrains.settings.LambdaSettings
import software.aws.toolkits.resources.message
import javax.swing.Icon

class LambdaLineMarker : LineMarkerProviderDescriptor() {

    override fun getName(): String? = message("lambda.service_name")

    override fun getIcon(): Icon? = AwsIcons.Resources.LAMBDA_FUNCTION

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process leaf elements
        if (element.firstChild != null) {
            return null
        }

        val runtimeGroup = element.language.runtimeGroup ?: return null
        val handlerResolver = LambdaHandlerResolver.getInstanceOrNull(runtimeGroup) ?: return null
        val handler = handlerResolver.determineHandler(element) ?: return null

        return if (handlerResolver.shouldShowLineMarker(handler) || shouldShowLineMarker(element.containingFile, handler, runtimeGroup)) {
            val actionGroup = DefaultActionGroup()

            val smartPsiElementPointer = SmartPointerManager.createPointer(element)

            if (element.language in LambdaBuilder.supportedLanguages()) {
                val executorActions = ExecutorAction.getActions(1)
                executorActions.forEach {
                    actionGroup.add(LineMarkerActionWrapper(element, it))
                }

                actionGroup.add(CreateLambdaFunction(handler, smartPsiElementPointer, handlerResolver))
            }

            object : LineMarkerInfo<PsiElement>(
                element,
                element.textRange,
                icon,
                null,
                null,
                GutterIconRenderer.Alignment.CENTER
            ) {
                override fun createGutterRenderer(): GutterIconRenderer? = LambdaGutterIcon(this, actionGroup)
            }
        } else null
    }

    private fun shouldShowLineMarker(psiFile: PsiFile, handler: String, runtimeGroup: RuntimeGroup): Boolean {
        val project = psiFile.project
        return LambdaSettings.getInstance(project).showAllHandlerGutterIcons ||
            handlerInTemplate(project, handler, runtimeGroup) ||
            handlerInRemote(psiFile, handler, runtimeGroup)
    }

    // Handler defined in template with the same runtime group is valid
    private fun handlerInTemplate(project: Project, handler: String, runtimeGroup: RuntimeGroup): Boolean =
        listFunctions(project).any {
            it.handler() == handler && Runtime.fromValue(it.runtime())?.runtimeGroup == runtimeGroup
        }

    // Handler defined in remote Lambda with the same runtime group is valid
    private fun handlerInRemote(psiFile: PsiFile, handler: String, runtimeGroup: RuntimeGroup): Boolean {
        if (!AwsConnectionManager.getInstance(psiFile.project).isValidConnectionSettings()) {
            return false
        }

        return when (val functions = psiFile.project.getResourceIfPresent(LambdaResources.LIST_FUNCTIONS)) {
            null -> {
                psiFile.project.getResource(LambdaResources.LIST_FUNCTIONS).whenComplete { _, _ ->
                    runReadAction {
                        DaemonCodeAnalyzer.getInstance(psiFile.project).restart(psiFile)
                    }
                }
                false
            }
            else -> functions.any { it.handler() == handler && it.runtime().runtimeGroup == runtimeGroup }
        }
    }

    class LambdaGutterIcon(markerInfo: LineMarkerInfo<PsiElement>, private val actionGroup: ActionGroup) :
        LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>(markerInfo) {
        override fun getClickAction(): AnAction? = null

        override fun isNavigateAction(): Boolean = true

        override fun getPopupMenuActions(): ActionGroup = actionGroup
    }
}
