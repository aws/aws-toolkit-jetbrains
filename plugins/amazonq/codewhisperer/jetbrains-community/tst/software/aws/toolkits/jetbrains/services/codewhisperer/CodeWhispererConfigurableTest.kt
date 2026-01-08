// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.dsl.builder.components.DslLabel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererConfigurable
import software.aws.toolkits.resources.message
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel

class CodeWhispererConfigurableTest : CodeWhispererTestBase() {

    @Test
    fun `test CodeWhisperer configurable`() {
        doNothing().whenever(codeScanManager).buildCodeScanUI()
        doNothing().whenever(codeScanManager).showCodeScanUI()
        doNothing().whenever(codeScanManager).removeCodeScanUI()
        val configurable = CodeWhispererConfigurable(projectRule.project)

        // A workaround to initialize disposable in the DslConfigurableBase since somehow the disposable is
        // not configured in the tests if we don't do this
        configurable.reset()

        assertThat(configurable.id).isEqualTo("aws.codewhisperer")
        val panel = configurable.createPanel()
        mockCodeWhispererEnabledStatus(false)

        val checkboxes = panel.components.filterIsInstance<JCheckBox>()

        assertThat(checkboxes.size).isEqualTo(6)
        assertThat(checkboxes.map { it.text }).containsExactlyInAnyOrder(
            message("aws.settings.codewhisperer.include_code_with_reference"),
            message("aws.settings.codewhisperer.configurable.opt_out.title"),
            message("aws.settings.codewhisperer.automatic_import_adder"),
            "Server-side context",
            message("aws.settings.codewhisperer.project_context"),
            message("aws.settings.codewhisperer.project_context_gpu")
        )

        val comments = panel.components.filterIsInstance<DslLabel>()
        assertThat(comments.size).isEqualTo(9)

        mockCodeWhispererEnabledStatus(false)
        ApplicationManager.getApplication().messageBus.syncPublisher(ToolkitConnectionManagerListener.TOPIC)
            .activeConnectionChanged(null)
        checkboxes.forEach {
            assertThat(it.isEnabled).isFalse
        }
        val firstMessage = panel.components.firstOrNull {
            it is JLabel && it.text == message("aws.settings.codewhisperer.warning")
        }
        assertThat(firstMessage).isNotNull
        assertThat((firstMessage as JComponent).isVisible).isTrue

        mockCodeWhispererEnabledStatus(true)
        ApplicationManager.getApplication().messageBus.syncPublisher(ToolkitConnectionManagerListener.TOPIC)
            .activeConnectionChanged(null)
        checkboxes.forEach {
            assertThat(it.isEnabled).isTrue
        }
        assertThat(firstMessage.isVisible).isFalse
    }
}
