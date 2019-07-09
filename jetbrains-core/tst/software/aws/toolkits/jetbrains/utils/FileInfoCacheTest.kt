// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class FileInfoCacheTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun cachedResultsAreReturned() {
        var callCount = 0
        val tempFile = tempFolder.newFile()
        val filePath = tempFile.absolutePath
        val info = "v1"
        tempFile.writeText(info)

        val infoProvider = object : FileInfoCache<String>() {
            override fun getFileInfo(path: String): String {
                callCount++
                return File(filePath).readText()
            }
        }

        assertThat(infoProvider.evaluateBlocking(filePath)).isEqualTo(info)
        assertThat(infoProvider.evaluateBlocking(filePath)).isEqualTo(info)
        assertThat(callCount).isEqualTo(1)
    }

    @Test
    fun exceptionLeadsToCheckingAgain() {
        var callCount = 0
        val tempFile = tempFolder.newFile()
        val filePath = tempFile.absolutePath
        val info = "v1"
        tempFile.writeText(info)

        val infoProvider = object : FileInfoCache<String>() {
            override fun getFileInfo(path: String): String {
                try {
                    if (callCount == 0) {
                        throw RuntimeException("Simulated exception")
                    } else {
                        return File(filePath).readText()
                    }
                } finally {
                    callCount++
                }
            }
        }

        assertThatThrownBy { infoProvider.evaluateBlocking(filePath) }

        Thread.sleep(1000)
        tempFile.setLastModified(Instant.now().toEpochMilli())

        assertThat(infoProvider.evaluateBlocking(filePath)).isEqualTo(info)
        assertThat(callCount).isEqualTo(2)
    }

    @Test
    fun updatingAFileLeadsToCheckingAgain() {
        var callCount = 0
        val tempFile = tempFolder.newFile()
        val filePath = tempFile.absolutePath
        var info = "v1"
        tempFile.writeText(info)

        val infoProvider = object : FileInfoCache<String>() {
            override fun getFileInfo(path: String): String {
                callCount++
                return File(filePath).readText()
            }
        }

        assertThat(infoProvider.evaluateBlocking(filePath)).isEqualTo(info)

        // Mac timestamp granularity is 1 sec
        Thread.sleep(1000)

        info = "v2"
        tempFile.writeText(info)

        assertThat(infoProvider.evaluateBlocking(filePath)).isEqualTo(info)
        assertThat(callCount).isEqualTo(2)
    }
}