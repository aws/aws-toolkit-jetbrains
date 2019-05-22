// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.string.printToString
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.BuiltLambda
import software.aws.toolkits.jetbrains.services.lambda.LambdaBuilder
import software.aws.toolkits.jetbrains.services.lambda.sam.SamOptions
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateUtils
import software.aws.toolkits.resources.message
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CompletionStage

class DotNetLambdaBuilder : LambdaBuilder() {

    companion object {
        private val templateYamlRegex = Regex("template\\.(yaml|yml|json)", RegexOption.IGNORE_CASE)
    }

    override fun buildLambda(
        module: Module,
        handlerElement: PsiElement,
        handler: String,
        runtime: Runtime,
        envVars: Map<String, String>,
        samOptions: SamOptions,
        onStart: (ProcessHandler) -> Unit
    ): CompletionStage<BuiltLambda> {
        val project = module.project
        val customTemplate = FileUtil.createTempFile("template", ".yaml", true)
        val logicalId = "Function"

        SamTemplateUtils.writeDummySamTemplate(
            tempFile = customTemplate,
            logicalId = logicalId,
            runtime = runtime,
            codeUri = getCodeUriPath(project, handler),
            handler = handler,
            envVars = envVars
        )

        return buildLambdaFromTemplate(module, customTemplate.toPath(), logicalId, samOptions, onStart)
    }

    private fun getCodeUriPath(project: Project, handlerString: String): String {
        val basePath = project.basePath ?: throw IllegalStateException(message("sam.init.error.no.project.basepath"))

        // Find YAML or JSON template.
        val templateFiles = File(basePath).walk().filter { it.name.matches(templateYamlRegex) }
        if (templateFiles.count() < 1)
            throw FileNotFoundException(message("lambda.sam.template_not_found", templateYamlRegex.printToString()))

        val templateFile = templateFiles.find { it.readText().contains(handlerString) }
                ?: throw IllegalStateException(message("lambda.upload_validation.handler_not_found"))

        // Find codeUri value.
        val codeUri = templateFile.readLines().find { it.contains("codeuri:", ignoreCase = true) }?.substringAfter(":")?.trim()
                ?: throw IllegalStateException(message("lambda.function.template.code_uri_not_found"))

        return templateFile.parentFile.resolve(codeUri).canonicalPath
    }
}
