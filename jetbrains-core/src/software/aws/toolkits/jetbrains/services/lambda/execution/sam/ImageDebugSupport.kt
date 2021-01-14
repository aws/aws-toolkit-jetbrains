// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.sam

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtil
import software.aws.toolkits.jetbrains.core.utils.buildList
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import java.util.UUID

interface ImageDebugSupport : SamDebugSupport<ImageTemplateRunSettings> {
    val id: String

    fun displayName(): String
    fun supportsPathMappings(): Boolean
    fun runtimeGroup(): RuntimeGroup

    override fun samArguments(debugPorts: List<Int>): List<String> = buildList {
        val containerEnvVars = containerEnvVars(debugPorts)
        if (containerEnvVars.isNotEmpty()) {
            val path = createContainerEnvVarsFile(containerEnvVars)
            add("--container-env-vars")
            add(path)
        }
    }

    fun containerEnvVars(debugPorts: List<Int>): Map<String, String> = emptyMap()

    private fun createContainerEnvVarsFile(envVars: Map<String, String>): String {
        val envVarsFile = FileUtil.createTempFile("${UUID.randomUUID()}-debugArgs", ".json", true)
        envVarsFile.writeText(mapper.writeValueAsString(envVars))
        return envVarsFile.absolutePath
    }

    companion object {
        private val mapper = jacksonObjectMapper()
        val EP_NAME = ExtensionPointName<ImageDebugSupport>("aws.toolkit.lambda.sam.imageDebuggerSupport")

        fun debuggers(): Map<String, ImageDebugSupport> = EP_NAME.extensionList.associateBy { it.id }
    }
}
