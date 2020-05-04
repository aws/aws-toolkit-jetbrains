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
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParsedFile

/**
 * Single entry point that encapsulates all logic that will fail if the underlying JSON support is not present.
 *
 * If [JsonCfnService.getInstance] returns null, this means that JSON support is not present and should not be acted upon.
 */
class JsonCfnService {
    fun isCloudFormation(element: PsiElement): Boolean {
        val psiFile = element.firstChild
        if (psiFile !is JsonFile) {
            return false
        }

        val fileType = psiFile.fileType
        return fileType == JsonCfnFileType.INSTANCE
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
