// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.applyToComponent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JTextField

@RunsInEdt
class ValidatingPanelTest {
    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Rule
    @JvmField
    val executeOnEdtRule = EdtRule()

    @Rule
    @JvmField
    val appRule = ApplicationRule()

    @Test
    fun `valid panels can execute actions`() {
        val testPanel = TestPanel(initialTextValue = "Valid text")

        assertThat(testPanel.validatingAction.isEnabled).isTrue
        assertThat(testPanel.normalAction.isEnabled).isTrue

        assertThat(testPanel.executeValidatingAction()).isTrue
        assertThat(testPanel.executeNormalAction()).isTrue
    }

    @Test
    fun `invalid panels can only execute normal actions`() {
        val testPanel = TestPanel()

        assertThat(testPanel.validatingAction.isEnabled).isTrue
        assertThat(testPanel.normalAction.isEnabled).isTrue

        assertThat(testPanel.executeValidatingAction()).isFalse
        assertThat(testPanel.executeNormalAction()).isTrue

        assertThat(testPanel.validatingAction.isEnabled).isFalse
        assertThat(testPanel.normalAction.isEnabled).isTrue
    }

    @Test
    fun `errors get applied on executing a validating action`() {
        val testPanel = TestPanel()

        assertThat(testPanel.textFieldValidator?.validationInfo).isNull()

        assertThat(testPanel.executeValidatingAction()).isFalse
        assertThat(testPanel.validatingAction.isEnabled).isFalse

        assertThat(testPanel.textFieldValidator?.validationInfo?.message).isNotEmpty
    }

    @Test
    fun `errors get applied and cleared on typing`() {
        val testPanel = TestPanel()

        assertThat(testPanel.textFieldValidator?.validationInfo).isNull()
        assertThat(testPanel.validatingAction.isEnabled).isTrue

        testPanel.textFieldComponent.text = "Foo"

        assertThat(testPanel.textFieldValidator?.validationInfo?.message).isNotEmpty
        assertThat(testPanel.validatingAction.isEnabled).isFalse

        testPanel.textFieldComponent.text = "Valid Text"

        assertThat(testPanel.textFieldValidator?.validationInfo).isNull()
        assertThat(testPanel.validatingAction.isEnabled).isTrue
    }

    @Test
    fun `valid panels can apply their state`() {
        val initialText = "Initial Text"
        val updatedText = "Updated Text"
        val testPanel = TestPanel(initialTextValue = initialText)

        assertThat(testPanel.textFieldComponent.text).isEqualTo(initialText)

        testPanel.textFieldComponent.text = updatedText

        assertThat(testPanel.executeValidatingAction()).isTrue

        assertThat(testPanel.testText).isEqualTo(updatedText)
    }

    inner class TestPanel(initialTextValue: String = "") {
        var testText = initialTextValue

        private val validatingActionName = "NeedsValidation"
        private val normalActionName = "DoesNotNeedValidation"
        private val testEvent = ActionEvent(this, ActionEvent.ACTION_PERFORMED, "test")
        private var validatingActionRan = false
        private var normalActionRan = false
        lateinit var textFieldComponent: JTextField
            private set

        private val panel = validatingPanel(disposableRule.disposable) {
            row {
                textField(::testText)
                    .withValidationOnInput { it.validateText() }
                    .withValidationOnApply { it.validateText() }
                    .applyToComponent {
                        textFieldComponent = this
                    }
            }

            actions {
                addAction(validatingActionName) {
                    validatingActionRan = true
                }

                addAction(normalActionName, requiresValidation = false) {
                    normalActionRan = true
                }
            }
        }

        private fun JBTextField.validateText() = if (this.text.length < 5) ValidationInfo("Test Error, '${this.text}'.length() < 5", this) else null

        val validatingAction: Action
            get() = panel.getAction(validatingActionName)!!

        fun executeValidatingAction(): Boolean {
            validatingActionRan = false
            validatingAction.actionPerformed(testEvent)
            return validatingActionRan
        }

        val normalAction: Action
            get() = panel.getAction(normalActionName)!!

        fun executeNormalAction(): Boolean {
            normalActionRan = false
            normalAction.actionPerformed(testEvent)
            return normalActionRan
        }

        val textFieldValidator: ComponentValidator?
            get() = ComponentValidator.getInstance(textFieldComponent).orElseGet(null)
    }
}
