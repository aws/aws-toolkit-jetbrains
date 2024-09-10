// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.tools.ide.metrics.benchmark.PerformanceTestUtil
import com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.isNull
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import kotlin.io.path.relativeTo

class CodeWhispererCodeFileScanPerfTest : CodeWhispererCodeScanTestBase(PythonCodeInsightTestFixtureRule()) {
    @Test
    fun `test run() - measure CPU and memory usage with payload of 200KB`() {
        // Create a 200KB file
        val content = "a".repeat(200 * 1024)
        val psiFile = projectRule.fixture.addFileToProject("test.txt", content)

        val sessionConfig = spy(
            CodeScanSessionConfig.create(
                psiFile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )
        setupResponse(psiFile.virtualFile.toNioPath().relativeTo(sessionConfig.projectRoot.toNioPath()))
        val sessionContext = CodeScanSessionContext(project, sessionConfig, CodeWhispererConstants.CodeAnalysisScope.FILE)
        val session = spy(CodeWhispererCodeScanSession(sessionContext))
        doNothing().whenever(session).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any())

        PerformanceTestUtil.newPerformanceTest("scan") {
            runTest {
                session.run()
            }
        }.withMetricsCollector(OpenTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) { true })
            .start()
    }

    @Test
    fun `test run() - measure CPU and memory usage with payload of 150KB`() {
        // Create a 150KB file
        val content = "a".repeat(150 * 1024)
        val psiFile = projectRule.fixture.addFileToProject("test.txt", content)

        val sessionConfig = spy(
            CodeScanSessionConfig.create(
                psiFile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )
        setupResponse(psiFile.virtualFile.toNioPath().relativeTo(sessionConfig.projectRoot.toNioPath()))
        val sessionContext = CodeScanSessionContext(project, sessionConfig, CodeWhispererConstants.CodeAnalysisScope.FILE)
        val session = spy(CodeWhispererCodeScanSession(sessionContext))
        doNothing().whenever(session).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any())

        PerformanceTestUtil.newPerformanceTest("scan") {
            runTest {
                session.run()
            }
        }.withMetricsCollector(OpenTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) { true })
            .start()
    }
}
