// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.impl.JsonPropertyImpl
import com.intellij.openapi.application.runReadAction
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
                isCloudFormationTemplate = isJsonTemplate(jsonFile)
            }
        } else if (psiFile.fileType is YAMLFileType) {
            val yamlFile = psiFile as YAMLFile
            runReadAction {
                isCloudFormationTemplate = isYamlTemplate(yamlFile)
            }
        }
    }

    private fun isYamlTemplate(yamlFile: YAMLFile): Boolean {
        val doc = YamlCloudFormationTemplate(yamlFile)
        val templateFormatVersion = doc.templateRoot?.getKeyValueByKey(TEMPLATE_VERSION_KEY)
        if (templateFormatVersion != null) {
            return true
        }
        val resources = doc.resources()
        resources.forEach {
            val resourceType = it.type()
            if (!resourceType.isNullOrEmpty()) {
                return true
            }
        }
        return false
    }

    private fun isJsonTemplate(jsonFile: JsonFile): Boolean {
        val doc = jsonFile.topLevelValue as JsonObject
        val templateFormatVersion = doc.findProperty(TEMPLATE_VERSION_KEY)
        if (templateFormatVersion != null) {
            return true
        }
        val resources = doc.findProperty(RESOURCES_KEY)
        if (resources == null || resources.value == null || resources.value !is JsonObject) {
            return false
        }

        (resources.value as JsonObject).children.forEach { resource ->
            (resource as JsonPropertyImpl).value?.children?.forEach { child ->
                val key = (child as JsonPropertyImpl).name
                if (key.toLowerCase() == "type") {
                    return true
                }
            }
        }
        return false
    }
}
