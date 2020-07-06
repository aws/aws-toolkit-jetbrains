// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.s3

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
                find<ComponentFixture>(byXpath("//div[@text='Create S3 Bucket']")).click()
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
                    find<ComponentFixture>(byXpath("//div[@text='Delete S3 Bucket']")).click()
                    find<JTextFieldFixture>(byXpath("//div[@class='JTextField']"), Duration.ofSeconds(5)).text = bucket
                    find<ComponentFixture>(byXpath("//div[@accessiblename='OK' and @class='JButton' and @text='OK']")).click()
                }
            }
        }
    }
}
