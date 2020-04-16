// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.projectwizard

import com.intellij.remoterobot.stepsProcessing.log
import com.intellij.remoterobot.stepsProcessing.step
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.jetbrains.uitests.fixtures.editor
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.newProjectWizard
import software.aws.toolkits.jetbrains.uitests.fixtures.preferencesDialog
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import software.aws.toolkits.jetbrains.uitests.rules.Ide
import software.aws.toolkits.jetbrains.uitests.rules.uiTest

class SamTemplateProjectWizardTest {
    companion object {
        @JvmField
        @ClassRule
        val ide = Ide(":jetbrains-core:runIdeForUiTests")

        @JvmStatic
        @BeforeClass
        fun setUpSamCli() {
            val samPath = System.getenv("SAM_CLI_EXEC")
            if (samPath.isNullOrEmpty()) {
                log.warn("No custom SAM set, skipping")
                return
            }

            uiTest {
                welcomeFrame {
                    step("Open preferences page") {
                        openPreferences()

                        preferencesDialog {
                            // Search for AWS because sometimes it is off the screen
                            search("AWS")

                            selectPreferencePage("Tools", "AWS")

                            step("Set SAM CLI executable path to $samPath") {
                                textField("SAM CLI executable:").text = samPath
                            }

                            pressOk()
                        }
                    }
                }
            }
        }
    }

    @JvmField
    @Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun createSamApp() {
        uiTest {
            welcomeFrame {
                openNewProjectWizard()

                step("Run New Project Wizard") {
                    newProjectWizard {
                        selectProjectCategory("AWS")
                        selectProjectType("AWS Serverless Application")

                        pressNext()

                        setProjectLocation(tempFolder.newFolder().absolutePath)

                        // TODO: Runtime
                        // TODO: Sam Template

                        pressFinish()
                    }
                }

                idea {
                    step("Validate Readme is opened") {
                        editor("README.md")
                    }
                }
            }
        }
    }
}
