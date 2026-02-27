// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel

class StackPanelLayoutBuilderTest {

    @Test
    fun `createTitleLabel creates label with correct styling`() {
        val label = StackPanelLayoutBuilder.createTitleLabel("Test Label")

        assertThat(label.text).isEqualTo("Test Label")
        assertThat(label.foreground).isEqualTo(UIUtil.getContextHelpForeground())
        assertThat(label.font.isBold).isTrue()
    }

    @Test
    fun `createFormPanel creates panel with GridBagLayout and default padding`() {
        val panel = StackPanelLayoutBuilder.createFormPanel()

        assertThat(panel.layout).isInstanceOf(GridBagLayout::class.java)
        assertThat(panel.border).isNotNull()
    }

    @Test
    fun `createFormPanel creates panel with custom padding`() {
        val panel = StackPanelLayoutBuilder.createFormPanel(30)

        assertThat(panel.layout).isInstanceOf(GridBagLayout::class.java)
        assertThat(panel.border).isNotNull()
    }

    @Test
    fun `addLabeledField adds label and component to panel`() {
        val panel = StackPanelLayoutBuilder.createFormPanel()
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }
        val testComponent = JBLabel("Test Component")

        val nextRow = StackPanelLayoutBuilder.addLabeledField(
            panel,
            gbc,
            0,
            "Test Field",
            testComponent
        )

        assertThat(nextRow).isEqualTo(2)
        assertThat(panel.componentCount).isEqualTo(2) // Label + component

        // Verify first component is the title label
        val titleLabel = panel.getComponent(0) as JBLabel
        assertThat(titleLabel.text).isEqualTo("Test Field")
        assertThat(titleLabel.font.isBold).isTrue()

        // Verify second component is our test component
        assertThat(panel.getComponent(1)).isEqualTo(testComponent)
    }

    @Test
    fun `addLabeledField with fillNone modifies constraints correctly`() {
        val panel = StackPanelLayoutBuilder.createFormPanel()
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }
        val testComponent = JBLabel("Test Component")

        StackPanelLayoutBuilder.addLabeledField(
            panel,
            gbc,
            0,
            "Test Field",
            testComponent,
            fillNone = true
        )

        // Constraints should be reset after the method
        assertThat(gbc.fill).isEqualTo(GridBagConstraints.HORIZONTAL)
        assertThat(gbc.anchor).isEqualTo(GridBagConstraints.NORTHWEST)
    }

    @Test
    fun `addLabeledField with isLast uses different insets`() {
        val panel = StackPanelLayoutBuilder.createFormPanel()
        val gbc = GridBagConstraints()
        val testComponent = JBLabel("Test Component")

        val nextRow = StackPanelLayoutBuilder.addLabeledField(
            panel,
            gbc,
            0,
            "Test Field",
            testComponent,
            isLast = true
        )

        assertThat(nextRow).isEqualTo(2)
        assertThat(panel.componentCount).isEqualTo(2)
    }

    @Test
    fun `addFiller adds empty panel with correct constraints`() {
        val panel = StackPanelLayoutBuilder.createFormPanel()
        val gbc = GridBagConstraints()

        StackPanelLayoutBuilder.addFiller(panel, gbc, 2)

        assertThat(panel.componentCount).isEqualTo(1)
        assertThat(panel.getComponent(0)).isInstanceOf(JPanel::class.java)
        assertThat(gbc.gridy).isEqualTo(4) // row + 2
        assertThat(gbc.weighty).isEqualTo(1.0)
        assertThat(gbc.fill).isEqualTo(GridBagConstraints.BOTH)
    }

    @Test
    fun `multiple addLabeledField calls increment rows correctly`() {
        val panel = StackPanelLayoutBuilder.createFormPanel()
        val gbc = GridBagConstraints()

        var row = 0
        row = StackPanelLayoutBuilder.addLabeledField(panel, gbc, row, "Field 1", JBLabel("Value 1"))
        row = StackPanelLayoutBuilder.addLabeledField(panel, gbc, row, "Field 2", JBLabel("Value 2"))
        row = StackPanelLayoutBuilder.addLabeledField(panel, gbc, row, "Field 3", JBLabel("Value 3"))

        assertThat(row).isEqualTo(6) // 3 fields * 2 rows each
        assertThat(panel.componentCount).isEqualTo(6) // 3 labels + 3 components
    }
}
