// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.fixtures.SearchTextFieldFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.findComponentWithTimeout
import com.intellij.testGuiFramework.impl.jTree
import com.intellij.testGuiFramework.impl.textfield
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.SearchTextField
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.setSamExecutableFromEnvironment
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutableIfPresent
import software.aws.toolkits.jetbrains.fixtures.openSettingsDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable

class SetSamCli : GuiTestCase() {
    @Test
    fun setSamCli() {
        setSamExecutableFromEnvironment()
        val samPath = ExecutableManager.getInstance().getExecutableIfPresent<SamExecutable>().let {
            when (it) {
                is ExecutableInstance.Executable -> it.executablePath.toString()
                else -> throw java.lang.IllegalStateException("SAM executable is not set in environment or path")
            }
        }
        welcomeFrame {
            step("Open preferences page") {
                openSettingsDialog()

                dialog(defaultSettingsTitle) {
                    // Search for AWS because sometimes it is off the screen
                    step("Search for AWS") {
                        findSearchTextField().click()

                        robot().enterText("AWS")
                    }

                    jTree("Tools", "AWS").clickPath()

                    step("Set SAM CLI executable path to $samPath") {
                        val execPath = textfield("SAM CLI executable:")
                        execPath.setText(samPath)
                    }
                    button("OK").click()
                }
            }
        }
    }

    private fun JDialogFixture.findSearchTextField(): SearchTextFieldFixture {
        val searchTextField = findComponentWithTimeout(this.target(), SearchTextField::class.java)
        return SearchTextFieldFixture(this.robot(), searchTextField)
    }
}
