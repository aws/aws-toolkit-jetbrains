// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

class OpenedFileTypeMetricsTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun `metrics are recorded for already opened file types`() = runTest {
        val dummyFile = LightVirtualFile("dummy.kt")
        runInEdtAndWait {
            FileEditorManager.getInstance(projectRule.project).openFile(dummyFile)
        }

        val openedFileTypeMetrics = spy(OpenedFileTypesMetrics())
        openedFileTypeMetrics.stub {
            on { openedFileTypeMetrics.scheduleNextMetricEvent() }.doAnswer {
                openedFileTypeMetrics.emitFileTypeMetric()
            }
        }

        openedFileTypeMetrics.execute(projectRule.project)

        verify(openedFileTypeMetrics).emitMetric(".kt")
    }

    @Test
    fun `duplicate metrics are not emitted`() = runTest {
        val testFile = LightVirtualFile("test1.kt")
        val testFile2 = LightVirtualFile("test2.kt")
        runInEdtAndWait {
            FileEditorManager.getInstance(projectRule.project).openFile(testFile)
            FileEditorManager.getInstance(projectRule.project).openFile(testFile2)
        }
        val openedFileTypeMetrics = spy(OpenedFileTypesMetrics())
        openedFileTypeMetrics.stub {
            on { openedFileTypeMetrics.scheduleNextMetricEvent() }.doAnswer {
                openedFileTypeMetrics.emitFileTypeMetric()
            }
        }

        openedFileTypeMetrics.execute(projectRule.project)

        verify(openedFileTypeMetrics).emitMetric(any())
    }
}
