// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.cloudformation.json.JSON_FILE_TYPE_NAME
import software.aws.toolkits.jetbrains.services.cloudformation.yaml.YAML_FILE_TYPE_NAME

class CfnFileTypeDetector : FileTypeRegistry.FileTypeDetector {
    override fun getVersion(): Int = 1

    override fun getDetectedFileTypes(): Collection<FileType> {
        // TODO: This is needed for 193, and marked for removal in 202...we will need to move it into a 193 only source set
        val registry = FileTypeRegistry.getInstance()
        return listOfNotNull(
            registry.findFileTypeByName(JSON_FILE_TYPE_NAME),
            registry.findFileTypeByName(YAML_FILE_TYPE_NAME)
        )
    }

    override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? {
        if (firstCharsIfText == null) {
            return null
        }

        if (file.extension != "template") {
            return null
        }

        if (SERVERLESS_TRANSFORM in firstCharsIfText || CFN_FORMAT_VERSION in firstCharsIfText) {
            val firstChar = firstCharsIfText.first { !it.isWhitespace() }

            // Note: We find by name in case it is not loaded due to upstream plugin (yaml/json) is not enabled
            val registry = FileTypeRegistry.getInstance()
            return if (firstChar == '{') {
                registry.findFileTypeByName(JSON_FILE_TYPE_NAME)
            } else {
                registry.findFileTypeByName(YAML_FILE_TYPE_NAME)
            }
        }

        return null
    }
}
