// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.doctypes.buildspec

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.jetbrains.yaml.YAMLFileType

class CodeBuildBuildSpecSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(
        object : JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile) =
                file.fileType is YAMLFileType &&
                    String(file.inputStream.readNBytes(1024), Charsets.UTF_8).let {
                        it.contains("version:") && it.contains("phases:")
                    }

            override fun getName() = "AWS CodeBuild Build Specificiation"

            override fun getSchemaFile() = JsonFileResolver.urlToFile(
                "https://d3rrggjwfhwld2.cloudfront.net/CodeBuild/buildspec/buildspec-standalone.schema.json"
            )

            override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

            override fun getSchemaType() = SchemaType.embeddedSchema
        }
    )
}
