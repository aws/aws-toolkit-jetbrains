// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.sam

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtil
import software.aws.toolkits.jetbrains.core.utils.buildList
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroupExtensionPointObject
import java.util.UUID

interface ImageDebugSupport : SamDebugSupport<ImageTemplateRunSettings> {
    val id: String

    fun displayName(): String
    fun supportsPathMappings(): Boolean
    fun runtimeGroup(): RuntimeGroup

    override fun patchCommandLine(cmdLine: GeneralCommandLine, debugPorts: List<Int>) {
        cmdLine.addParameters( buildList {
            val containerEnvVars = containerEnvVars(debugPorts)
            if (containerEnvVars.isNotEmpty()) {
                createContainerEnvVarsFile(containerEnvVars)
            }
        })
    }

    fun containerEnvVars(debugPorts: List<Int>): Map<String, String> = emptyMap()

    private fun createContainerEnvVarsFile(envVars: Map<String, String>): String {
        val envVarsFile = FileUtil.createTempFile("${UUID.randomUUID()}-debugArgs", ".json", true)
        envVarsFile.writeText(mapper.writeValueAsString(envVars))
        return envVarsFile.absolutePath
    }

    companion object : RuntimeGroupExtensionPointObject<ImageDebugSupport>(ExtensionPointName("aws.toolkit.lambda.sam.imageDebuggerSupport")) {
        private val mapper = jacksonObjectMapper()

        fun debuggers(): Map<String, ImageDebugSupport> = extensionList.associateBy { it.id }
    }
}
