// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import software.aws.toolkits.jetbrains.services.cloudformation.yaml.YamlCloudFormationTemplate

class InitialAnnotationResults(psiFile: PsiFile) {

    private val TEMPLATE_VERSION_KEY = "AWSTemplateFormatVersion"
    private val RESOURCES_KEY = "Resources"

    var isCloudFormationTemplate = false

    val pathToTemplate = psiFile.virtualFile.path

    init {
        if (psiFile.fileType is JsonFileType) {
            val jsonFile = psiFile as JsonFile
            runReadAction {
                val doc = jsonFile.topLevelValue as JsonObject
                val templateFormatVersion = doc.findProperty(TEMPLATE_VERSION_KEY)
                val resources = doc.findProperty(RESOURCES_KEY)
                isCloudFormationTemplate(templateFormatVersion, resources)
            }
        } else if (psiFile.fileType is YAMLFileType) {
            val yamlFile = psiFile as YAMLFile
            runReadAction {
                val doc = YamlCloudFormationTemplate(yamlFile)
                val templateFormatVersion = doc.templateRoot?.getKeyValueByKey(TEMPLATE_VERSION_KEY)
                val resources = doc.templateRoot?.getKeyValueByKey(RESOURCES_KEY)
                isCloudFormationTemplate(templateFormatVersion, resources)
            }
        }
    }

    private fun isCloudFormationTemplate(
        templateFormatVersion: PsiElement?,
        resources: PsiElement?
    ) {
        if (templateFormatVersion != null || resources != null) {
            isCloudFormationTemplate = true
        }
    }
}
