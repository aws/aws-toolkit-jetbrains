// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.text.nullize
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.resources.message
import java.awt.event.ActionListener
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton

open class ExecutableSelector<T : ExecutableType2<*>>(private val type: T, private val validationResultHolder: Wrapper) : BorderLayoutPanel() {
    private val testButton = JButton("Check")
    private val pathSelector = TextFieldWithBrowseButton(null as ActionListener?)
    private var autoDetectedPath = ""

    init {
        pathSelector.addBrowseFolderListener(
            "Select Executable",
            null,
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )

        testButton.addActionListener {
            val pathToCheck = getConfiguredPath() ?: autoDetectedPath
            val label = when (val validity = checkExecutable(Paths.get(pathToCheck), type)) {
                is Validity.Valid -> {
                    JBLabel("<html>${type.displayName} version is ${validity.version.displayValue()}")
                }
                else -> {
                    val message = "<html>${validity.toErrorMessage(type)?.replace("\n", "<br/>")}</html>"
                    JBLabel(message).apply {
                        foreground = DialogWrapper.ERROR_FOREGROUND_COLOR
                    }
                }
            }

            validationResultHolder.setContent(label)

            this.revalidate()
        }

        addToCenter(pathSelector)
        addToRight(testButton)
    }

    fun reset() {
        autoDetectedPath = ExecutableManager2.getInstance().detectExecutable(type)?.toString() ?: ""

        val emptyText = if (autoDetectedPath.isNotBlank()) {
            message("executableCommon.auto_resolved", autoDetectedPath)
        } else {
            ""
        }
        (pathSelector.textField as? JBTextField)?.emptyText?.text = emptyText
    }

    fun apply() {
        ExecutableSettings.getInstance().setExecutablePath(type, getConfiguredPath())
    }

    private fun getConfiguredPath(): String? = pathSelector.text.trim().nullize()

    open fun checkExecutable(executable: Path, type: T): Validity = ExecutableManager2.getInstance().validateCompatability(
        project = null,
        path = executable,
        type = this.type
    )
}
