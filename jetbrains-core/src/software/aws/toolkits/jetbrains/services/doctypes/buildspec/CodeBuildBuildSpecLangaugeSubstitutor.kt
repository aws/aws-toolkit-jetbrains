// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.doctypes.buildspec

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.yaml.YAMLLanguage

class CodeBuildBuildSpecLangaugeSubstitutor : LanguageSubstitutor() {
    override fun getLanguage(file: VirtualFile, project: Project): Language? {
        if (String(file.inputStream.readAllBytes(), Charsets.UTF_8).contains("version:")) {
            return YAMLLanguage.INSTANCE
        }

        return null
    }
}
