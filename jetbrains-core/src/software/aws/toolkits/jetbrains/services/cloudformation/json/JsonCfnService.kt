// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import software.aws.toolkits.jetbrains.services.cloudformation.CFN_FORMAT_VERSION
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParsedFile
import software.aws.toolkits.jetbrains.services.cloudformation.SERVERLESS_TRANSFORM

/**
 * Acts a proxy that hides all the logic that requires JSON plugin to be enabled
 */
class JsonCfnService {
    fun isCloudFormation(element: PsiElement): Boolean {
        val psiFile = element.firstChild
        if (psiFile !is JsonFile) {
            return false
        }

        val fileType = psiFile.fileType
        return if (fileType == JsonCfnFileType.INSTANCE) {
            true
        } else {
            val rootElement = rootElement(psiFile)

            rootElement?.findProperty(CFN_FORMAT_VERSION) != null || rootElement?.findProperty("Transform")?.value?.textMatches(SERVERLESS_TRANSFORM) == true
        }
    }

    private fun rootElement(file: JsonFile): JsonObject? = PsiTreeUtil.getChildOfType(file, JsonObject::class.java)

    fun parse(file: PsiFile): CfnParsedFile? {
        val rootElement = rootElement(file as JsonFile) ?: return null
        return JsonCfnParser.parse(rootElement)
    }

    companion object {
        fun getInstance(project: Project): JsonCfnService? = ServiceManager.getService(project, JsonCfnService::class.java)
    }
}
