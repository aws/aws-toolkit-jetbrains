// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import java.awt.Font

internal class WrappingTextArea(text: String) : JBTextArea(text) {
    init {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        font = font.deriveFont(Font.PLAIN)
        border = null
    }

    override fun getMinimumSize(): Dimension = Dimension(50, 20)
}
