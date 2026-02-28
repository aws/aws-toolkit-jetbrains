// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal object StackPanelLayoutBuilder {

    private const val DEFAULT_PADDING = 20
    private const val FIELD_SPACING = 12

    fun createTitleLabel(text: String): JBLabel = JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = font.deriveFont(Font.BOLD)
    }

    fun createFormPanel(padding: Int = DEFAULT_PADDING): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
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
        gbc.insets = if (isLast) JBUI.emptyInsets() else JBUI.insetsBottom(FIELD_SPACING)
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

    fun createTableWithPaginationPanel(
        title: String,
        consoleLink: JComponent,
        pageLabel: JComponent,
        prevButton: JButton,
        nextButton: JButton,
        table: JBTable,
    ): JComponent = panel {
        row {
            panel {
                row {
                    label(title).bold()
                    cell(consoleLink)
                }
            }
            panel {
                row {
                    cell(pageLabel)
                    cell(prevButton)
                    cell(nextButton)
                }
            }.align(AlignX.RIGHT)
        }
        row {
            scrollCell(table).align(Align.FILL)
        }.resizableRow()
    }
}
