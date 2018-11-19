// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import software.aws.toolkits.jetbrains.services.cloudformation.yaml.YamlCloudFormationTemplate
import java.io.File

interface CloudFormationTemplate {
    fun resources(): Sequence<Resource>

    fun getResourceByName(logicalName: String): Resource? = resources().firstOrNull { it.logicalName == logicalName }

    fun saveTo(file: File) {
        FileUtil.createIfNotExists(file)
        file.writeText(text())
    }

    fun text(): String

    companion object {
        fun parse(project: Project, templateFile: VirtualFile): CloudFormationTemplate = when {
            isYaml(templateFile) -> YamlCloudFormationTemplate(project, templateFile)
            else -> throw UnsupportedOperationException("Only YAML CloudFormation templates are supported")
        }

        fun convertPsiToResource(psiElement: PsiElement): Resource? = when (psiElement.language) {
            YAMLLanguage.INSTANCE -> YamlCloudFormationTemplate.convertPsiToResource(psiElement)
            else -> throw UnsupportedOperationException("Only YAML CloudFormation templates are supported")
        }

        private fun isYaml(templateFile: VirtualFile): Boolean = templateFile.fileType == YAMLFileType.YML ||
                templateFile.extension?.toLowerCase() in YAML_EXTENSIONS

        private val YAML_EXTENSIONS = setOf("yaml", "yml")
    }
}

interface Resource {
    val logicalName: String

    fun isType(requestedType: String): Boolean
    fun type(): String?
    fun getScalarProperty(key: String): String
    fun setScalarProperty(key: String, value: String)
}