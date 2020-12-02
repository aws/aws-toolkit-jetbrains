// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import software.aws.toolkits.jetbrains.services.cloudformation.json.JsonCfnService

data class CfnParsedFile(val root: CfnRootNode, val formatProblems: List<CfnProblem>)

object CfnParser {
    private val PARSED_TEMPLATE = Key.create<CachedValue<CfnParsedFile>>("PARSED_CFN_TEMPLATE")

    fun parse(file: PsiFile): CfnParsedFile = CachedValuesManager.getCachedValue(file, PARSED_TEMPLATE) {
        Result.create(createParsedFile(file), file)
    }

    private fun createParsedFile(file: PsiFile): CfnParsedFile? {
        val project = file.project
        val jsonCfnService = JsonCfnService.getInstance(project)

        if (jsonCfnService?.isCloudFormation(file) == true) {
            return jsonCfnService.parse(file)
        }

        // TODO: Yaml Service

        return null
    }
}
