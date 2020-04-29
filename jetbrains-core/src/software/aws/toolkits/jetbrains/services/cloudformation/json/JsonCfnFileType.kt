// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.FileType
import software.aws.toolkits.jetbrains.services.cloudformation.BaseCfnFileType

const val JSON_FILE_TYPE_NAME = "AWS CloudFormation (JSON)"

class JsonCfnFileType : BaseCfnFileType(JsonLanguage.INSTANCE) {
    override val baseFileType: FileType = JsonFileType.INSTANCE

    override fun getName(): String = JSON_FILE_TYPE_NAME
    override fun getDescription(): String = "AWS CloudFormation templates (JSON)"

    companion object {
        @JvmStatic
        val INSTANCE = JsonCfnFileType()
    }
}
