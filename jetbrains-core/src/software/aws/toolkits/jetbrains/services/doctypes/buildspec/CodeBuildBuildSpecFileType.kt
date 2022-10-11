// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.doctypes.buildspec

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.yaml.YAMLLanguage
import javax.swing.Icon

class CodeBuildBuildSpecFileType private constructor(): LanguageFileType(YAMLLanguage.INSTANCE, true) {
    override fun getName(): String = "AWS CodeBuild BuildSpec"

    override fun getDescription(): String = ""

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = AllIcons.FileTypes.Yaml

    companion object {
        @JvmField
        val INSTANCE = CodeBuildBuildSpecFileType()
    }
}
