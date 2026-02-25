// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.utils

import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.textField
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.slf4j.LoggerFactory
import java.awt.Point
import java.nio.file.Path
import java.util.Locale

object IdeStarterTestUtils {
    private val LOG = LoggerFactory.getLogger(IdeStarterTestUtils::class.java)
    fun UiRobot.findAndClick(xpath: String) {
        searchService.findAll(xpath).firstOrNull()?.let { component ->
            val point = Point(component.x + component.width / 2, component.y + component.height / 2)
            clickMouse(point)
        }
    }
    fun setupSamCliWithStarter(tempDir: Path) {
        val samPath = System.getenv("SAM_CLI_EXEC")
        if (samPath.isNullOrEmpty()) {
            LOG.warn("No custom SAM set, skipping setup")
            return
        }

        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(tempDir)
        )

        Starter.newContext("setupSamCli", testCase).apply {
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                // Open Preferences/Settings
                ui.keyboard {
                    if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("mac")) {
                        hotKey(157, 44) // Cmd + , for Mac
                    } else {
                        hotKey(17, 18, 83) // Ctrl + Alt + S for Windows/Linux
                    }
                }

                ui.textField("//div[@class='SearchField']").text = "AWS"

                ui.findAndClick("//div[text='Tools']")
                ui.findAndClick("//div[text='AWS']")

                ui.textField("//div[@text='SAM CLI executable:']").text = samPath

                ui.findAndClick("//button[text='OK']")

                ui.findAndClick("//div[text='Projects']")
            }
    }
}
