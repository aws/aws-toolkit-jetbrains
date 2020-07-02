// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.s3

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.newProjectWizard
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class S3BrowserTest {
    private val profile = "default"
    private val region = "us-west-2"
    private val credential = "Profile:$profile"
    private val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    private val bucket = "uitest-$date-${UUID.randomUUID()}"

    @TempDir
    lateinit var tempDir: Path

    @Test
    @CoreTest
    fun testS3Browser() = uiTest {
        welcomeFrame {
            openNewProjectWizard()
            newProjectWizard {
                pressNext()
                pressNext()
                setProjectLocation(tempDir.toAbsolutePath().toString())
                pressFinish()
            }
        }
        idea {
            waitForBackgroundTasks()
            setCredentials()
            toggleAwsExplorer()
        }
    }
}
