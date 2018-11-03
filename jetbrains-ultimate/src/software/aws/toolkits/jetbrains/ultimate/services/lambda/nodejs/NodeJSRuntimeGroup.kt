// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ultimate.services.lambda.nodejs

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.text.SemVer
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroupInformation

class NodeJSRuntimeGroup : RuntimeGroupInformation {
    override val runtimes: Set<Runtime> = setOf(
            Runtime.NODEJS,
            Runtime.NODEJS8_10,
            Runtime.NODEJS6_10,
            Runtime.NODEJS4_3,
            Runtime.NODEJS4_3_EDGE
    )

    override val languageIds: Set<String> = setOf(
            JavascriptLanguage.INSTANCE.id,
            JavaScriptSupportLoader.TYPESCRIPT.id,
            JavaScriptSupportLoader.FLOW_JS.id,
            JavaScriptSupportLoader.ECMA_SCRIPT_6.id
    )

    override fun determineRuntime(project: Project): Runtime? = null

    override fun determineRuntime(module: Module): Runtime? {
        val manager = NodeJsInterpreterManager.getInstance(module.project)
        val version = manager.interpreter?.cachedVersion?.get()
        if (version != null) {
            if (version < SemVer("6.10.0", 6, 10, 0)) return Runtime.NODEJS4_3
            if (version < SemVer("8.1.0", 8, 1, 0)) return Runtime.NODEJS6_10
            return Runtime.NODEJS8_10
        }
        return Runtime.NODEJS
    }
}