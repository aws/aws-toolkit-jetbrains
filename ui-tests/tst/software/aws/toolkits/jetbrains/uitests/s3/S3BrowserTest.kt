// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.s3

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.BaseUiTest
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.JTreeFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.awsExplorer
import software.aws.toolkits.jetbrains.uitests.fixtures.fillSingleTextField
import software.aws.toolkits.jetbrains.uitests.fixtures.findAndClick
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.pressDelete
import software.aws.toolkits.jetbrains.uitests.fixtures.pressOk
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@TestInstance(Lifecycle.PER_CLASS)
class S3BrowserTest : BaseUiTest() {
    private val profile = "default"
    private val credential = "Profile:$profile"
    private val region = "Oregon (us-west-2)"
    private val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    private val bucket = "uitest-$date-${UUID.randomUUID()}"
    private val folder = UUID.randomUUID().toString()

    private val S3 = "S3"
    private val createBucketText = "Create S3 Bucket"
    private val deleteBucketText = "Delete S3 Bucket"
    private val upload = "Upload..."
    private val newFolder = "New folder..."
    private val rename = "Rename..."
    private val delete = "Delete..."

    private val jsonFile = "hello.json"
    private val jsonFile2 = "hello2.json"
    private val newJsonName = "helloooooooooo.json"

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
        }
        idea {
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

            step("Upload object to top-level") {
                findAndClick("//div[@accessiblename='$upload' and @class='ActionButton']")
                fillSingleTextField(testDataPath.resolve("testFiles").resolve(jsonFile).toString())
                pressOk()
                // Wait for the item to be uploaded
                Thread.sleep(1000)
                s3Tree {
                    findText(jsonFile)
                }
            }

            step("Create folder") {
                findAndClick("//div[@accessiblename='$newFolder' and @class='ActionButton']")
                fillSingleTextField(folder)
                pressOk()
                // Wait for the folder to be created
                Thread.sleep(1000)
                s3Tree {
                    findText(folder)
                }
            }

            step("Upload object to folder") {
                // TODO have to use findText instead of the reasonable clickRow or clickPath because
                // it can't find anything for some reason
                s3Tree {
                    findText(folder).click()
                }
                findAndClick("//div[@accessiblename='$upload' and @class='ActionButton']")
                fillSingleTextField(testDataPath.resolve("testFiles").resolve(jsonFile2).toString())
                pressOk()
                // Wait for the item to be uploaded
                Thread.sleep(1000)
                s3Tree {
                    findText(folder).doubleClick()
                    findText(jsonFile2)
                }
            }

            step("Rename a file") {
                s3Tree {
                    findText(jsonFile).click()
                }
                findAndClick("//div[@accessiblename='$rename' and @class='ActionButton']")
                fillSingleTextField(newJsonName)
                pressOk()
                // Wait for the item to be renamed
                Thread.sleep(1000)
                s3Tree {
                    findText(newJsonName)
                }
            }

            step("Delete a file") {
                s3Tree {
                    // Reopen the folder
                    findText(folder).doubleClick()
                    findText(jsonFile2).click()
                }
                findAndClick("//div[@accessiblename='$delete' and @class='ActionButton']")
                pressDelete()
                // Wait for the item to be deleted
                Thread.sleep(1000)
                // make sure it's gone
                s3Tree {
                    // Attempt to reopen the folder
                    findText(folder).doubleClick()
                    assertThat(findAllText(jsonFile2)).isEmpty()
                }
            }

            step("Open known file-types") {
                s3Tree {
                    findText(newJsonName).doubleClick()
                }
                // Wait for the item to download and open
                Thread.sleep(1000)
                // Find the title bar
                find<ComponentFixture>(byXpath("//div[@accessiblename='$newJsonName' and @class='NavBarItem']"))
            }
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
        find<JTreeFixture>(byXpath("//div[@class='S3TreeTable']"), Duration.ofSeconds(5)).apply(func)
    }
}
