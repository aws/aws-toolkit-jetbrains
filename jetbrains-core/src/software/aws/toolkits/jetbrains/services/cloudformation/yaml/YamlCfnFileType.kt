// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.yaml

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import software.aws.toolkits.jetbrains.services.cloudformation.BaseCfnFileType

const val YAML_FILE_TYPE_NAME = "AWS CloudFormation (YAML)"

class YamlCfnFileType : BaseCfnFileType(YAMLLanguage.INSTANCE) {
    override val baseFileType: FileType = YAMLFileType.YML

    override fun getName(): String = YAML_FILE_TYPE_NAME
    override fun getDescription(): String = "AWS CloudFormation templates (YAML)"

    companion object {
        @JvmStatic
        val INSTANCE = YamlCfnFileType()
    }
}
