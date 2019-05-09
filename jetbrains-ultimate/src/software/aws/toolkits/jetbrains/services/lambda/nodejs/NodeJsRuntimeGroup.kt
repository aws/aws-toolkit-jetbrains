// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.nodejs

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.WebModuleTypeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.SdkBasedRuntimeGroupInformation

class NodeJsRuntimeGroup : SdkBasedRuntimeGroupInformation() {
    override val runtimes: Set<Runtime> = setOf(
        // TODO add nodejs10 support
        Runtime.NODEJS8_10
    )

    override val languageIds: Set<String> = setOf(JavascriptLanguage.INSTANCE.id)

    override fun determineRuntime(module: Module): Runtime? = determineRuntime(module.project)

    override fun determineRuntime(project: Project): Runtime? =
        NodeJsInterpreterManager.getInstance(project).interpreter?.cachedVersion?.get()?.let {
            when {
                // How do we decide which runtime to use?
                it.major <= 8 -> Runtime.NODEJS8_10
                // TODO add nodejs10 support
                else -> null
            }
        }

    /**
     * JavaScript does not define SDK. We override [determineRuntime] for fetching the correct Runtime.
     */
    override fun runtimeForSdk(sdk: Sdk): Runtime? = null

    override fun getModuleType(): ModuleType<*>? = WebModuleTypeBase.getInstance()

    override fun supportsSamBuild(): Boolean = true
}