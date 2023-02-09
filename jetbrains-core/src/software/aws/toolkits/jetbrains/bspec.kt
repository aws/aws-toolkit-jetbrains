// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class bspec : LanguageFileType(bspecLang.INSTANCE) {
    override fun getName(): String = "bspec"

    override fun getDescription(): String = "bspec"

    override fun getDefaultExtension(): String = "bspec"
    override fun getIcon() = null

    companion object {
        val INSTANCE = bspec()
    }
}

class bspecLang : Language("bspec") {
    companion object {
        val INSTANCE = bspecLang()
    }
}
