// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

object StackPanelLayoutBuilder {

    fun createTitleLabel(text: String): JBLabel = JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground() // Muted color like --vscode-descriptionForeground
        font = font.deriveFont(Font.BOLD) // Semi-bold like font-weight: 600
    }

    fun createFormPanel(padding: Int = 20): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
        border = JBUI.Borders.empty(padding)
    }

    fun addLabeledField(
        parent: JPanel,
        gbc: GridBagConstraints,
        startRow: Int,
        labelText: String,
        component: JComponent,
        fillNone: Boolean = false,
        isLast: Boolean = false,
    ): Int {
        // Add label
        gbc.gridx = 0
        gbc.gridy = startRow
        gbc.insets = JBUI.emptyInsets()
        parent.add(createTitleLabel(labelText), gbc)

        // Add component
        gbc.gridy = startRow + 1
        gbc.insets = if (isLast) JBUI.emptyInsets() else JBUI.insetsBottom(12)
        if (fillNone) {
            gbc.fill = GridBagConstraints.NONE
            gbc.anchor = GridBagConstraints.WEST
        }
        parent.add(component, gbc)

        // Reset constraints
        if (fillNone) {
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.anchor = GridBagConstraints.NORTHWEST
        }

        return startRow + 2
    }

    fun addFiller(parent: JPanel, gbc: GridBagConstraints, row: Int) {
        gbc.gridy = row + 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        parent.add(JPanel(), gbc)
    }
}
