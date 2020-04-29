// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons.Resources.CLOUDFORMATION_STACK
import software.aws.toolkits.core.utils.getLogger
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import javax.swing.Icon

abstract class BaseCfnFileType(language: Language) : LanguageFileType(language, true), FileTypeIdentifiableByVirtualFile {
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon = CLOUDFORMATION_STACK

    protected abstract val baseFileType: FileType

    override fun isMyFileType(file: VirtualFile): Boolean {
        val originalFileType = FileTypeManager.getInstance().getFileTypeByFileName(file.nameSequence)
        if (originalFileType != baseFileType) {
            return false
        }

        val bytes = try {
            FileUtil.loadFirstAndClose(file.inputStream, 10 * 1024)
        } catch (_: FileNotFoundException) {
            return false
        } catch (t: Throwable) {
            getLogger<BaseCfnFileType>().warn("Unable to read first bytes of file ${file.path}", t)
            return false
        }

        val text = LoadTextUtil.getTextByBinaryPresentation(bytes, StandardCharsets.UTF_8)

        if (SERVERLESS_TRANSFORM in text || CFN_FORMAT_VERSION in text) {
            return true
        }

        return false
    }
}
