// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.caws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import software.aws.toolkits.resources.message
import java.net.URL

class DevFileSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(
        object : JsonSchemaFileProvider {
            override fun getName(): String = message("caws.devfile.schema")

            override fun isAvailable(file: VirtualFile): Boolean = file.name.matches(Regex("devfile\\.y[a]?ml", RegexOption.IGNORE_CASE))

            override fun getSchemaFile(): VirtualFile? {
                val latestTag = jacksonObjectMapper().readTree(URL("https://api.github.com/repos/devfile/api/releases/latest")).get("tag_name").textValue()
                val schemaUrl: String = "https://raw.githubusercontent.com/devfile/api/$latestTag/schemas/latest/devfile.json"
                return JsonFileResolver.urlToFile(schemaUrl)}

            override fun getSchemaType(): SchemaType = SchemaType.remoteSchema

            override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7
        }
    )

}
