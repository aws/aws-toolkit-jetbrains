// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.text.nullize
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.resources.message
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton

open class ExecutableSelector(private val type: ExecutableType2<*>, disposable: Disposable) : BorderLayoutPanel() {
    private val testButton = JButton("Check")
    private val pathSelector = TextFieldWithBrowseButton(null, disposable)
    private val errorHolder = Wrapper()
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
            val executable = checkExecutable(Paths.get(pathToCheck))

            val errorComponent = executable.toErrorMessage(type)?.let {
                val htmlLabel = "<html>${it.replace("\n", "<br/>")}</html>"
                JBLabel(htmlLabel).apply {
                    foreground = DialogWrapper.ERROR_FOREGROUND_COLOR
                }
            }

            errorHolder.setContent(errorComponent)

            this.revalidate()
        }

        addToCenter(pathSelector)
        addToRight(testButton)
        addToBottom(errorHolder)
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

    open fun checkExecutable(executable: Path): Validity = ExecutableManager2.getInstance().validateCompatability(
        project = null,
        path = executable,
        type = type
    )
}
