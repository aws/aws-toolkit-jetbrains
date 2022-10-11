// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.doctypes.buildspec

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.YAMLFileType

class CodeBuildBuildSpecFileTypeDetector : FileTypeRegistry.FileTypeDetector {
    override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? =
        firstCharsIfText?.let {
            if (it.contains("version:") &&
                it.contains("phases:")) {
                return CodeBuildBuildSpecFileType.INSTANCE
            }
            return null
        }
}
