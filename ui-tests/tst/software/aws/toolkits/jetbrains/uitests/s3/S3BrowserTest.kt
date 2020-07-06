// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.s3

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.JTreeFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.awsExplorer
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@TestInstance(Lifecycle.PER_CLASS)
class S3BrowserTest {
    private val profile = "default"
    private val credential = "Profile:$profile"
    private val region = "Oregon (us-west-2)"
    private val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    private val bucket = "uitest-$date-${UUID.randomUUID()}"

    private val createBucketText = "Create S3 Bucket"
    private val deleteBucketText = "Delete S3 Bucket"
    private val upload = "Upload..."

    private val S3 = "S3"

    @TempDir
    lateinit var tempDir: Path

    @Test
    @CoreTest
    fun testS3Browser() = uiTest {
        welcomeFrame {
            newProject(tempDir)
        }
        idea {
            waitForBackgroundTasks()
            setCredentials(credential, region)
            showAwsExplorer()
            step("Create bucket named $bucket") {
                awsExplorer {
                    openExplorerActionMenu(S3)
                }
                find<ComponentFixture>(byXpath("//div[@text='$createBucketText']")).click()
                find<JTextFieldFixture>(byXpath("//div[@class='JTextField']"), Duration.ofSeconds(5)).text = bucket
                find<ComponentFixture>(byXpath("//div[@text='Create']")).click()
            }

            // Wait for the bucket to be created
            Thread.sleep(10000)

            awsExplorer {
                step("Open editor for bucket $bucket") {
                    expandExplorerNode(S3)
                    doubleClickExplorer(S3, bucket)
                }
            }


            s3Tree {

            }

            /* TODO
            step("Upload object to top-level") {
            }
            */

            step("Create folder") {
                rightClick()
                clickMenuItem { it.text.contains(NEW_FOLDER_ACTION) }
                dialog(NEW_FOLDER_ACTION) {
                    textfield(null).setText(FOLDER)
                    button(OK_BUTTON).clickWhenEnabled()
                }

                waitAMoment()

                assertNotNull(findPath(FOLDER))
            }

            step("Upload object to folder") {
                rightClick(0, FOLDER)
                clickMenuItem { it.text.contains(UPLOAD_ACTION) }
                fileChooserDialog {
                    setPath(testDataPath.resolve("testFiles").resolve(JSON_FILE).toString())
                    clickOk()
                }

                waitAMoment()

                doubleClick(0, FOLDER)

                assertNotNull(findPath(FOLDER, JSON_FILE))
            }

            step("Rename a file") {
                rightClick(0, FOLDER, JSON_FILE)
                clickMenuItem { it.text.contains(RENAME_ACTION) }

                dialog(RENAME_ACTION) {
                    textfield(null).setText(NEW_JSON_FILE_NAME)
                    button(OK_BUTTON).clickWhenEnabled()
                }

                waitAMoment()

                assertNotNull(findPath(FOLDER, NEW_JSON_FILE_NAME))
            }

            step("Delete a file") {
                rightClick(0, FOLDER, NEW_JSON_FILE_NAME)
                clickMenuItem { it.text.contains(DELETE_ACTION) }

                findMessageDialog(DELETE_ACTION).click(DELETE_PREFIX)

                waitAMoment()

                assertNull(findPath(FOLDER, NEW_JSON_FILE_NAME))
            }

            step("Open known file-types") {
                doubleClick(0, JSON_FILE)

                waitAMoment()

                assertNotNull(FileEditorManager.getInstance(project).allEditors.mapNotNull { it.file }.find {
                    it.name.contains(JSON_FILE) && it.fileType::class.simpleName?.contains("JsonFileType") == true
                })
            }*/
        }
    }

    @AfterAll
    fun cleanup() {
        step("Delete bucket named $bucket") {
            uiTest {
                idea {
                    showAwsExplorer()
                    awsExplorer {
                        openExplorerActionMenu(S3, bucket)
                    }
                    find<ComponentFixture>(byXpath("//div[@text='$deleteBucketText']")).click()
                    find<JTextFieldFixture>(byXpath("//div[@class='JTextField']"), Duration.ofSeconds(5)).text = bucket
                    find<ComponentFixture>(byXpath("//div[@accessiblename='OK' and @class='JButton' and @text='OK']")).click()
                }
            }
        }
    }

    private fun RemoteRobot.s3Tree(func: (JTreeFixture.() -> Unit)) {
        find<JTreeFixture>(byXpath("//div[@class='S3TreeTable']")).apply(func)
    }
}
