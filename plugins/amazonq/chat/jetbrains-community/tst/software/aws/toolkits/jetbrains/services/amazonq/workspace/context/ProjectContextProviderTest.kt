// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.workspace.context

import com.intellij.openapi.project.Project
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cwc.editor.context.project.EncoderServer
import software.aws.toolkits.jetbrains.services.cwc.editor.context.project.ProjectContextProvider
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import java.net.ConnectException
import kotlin.test.Test

class ProjectContextProviderTest {
    @Rule
    @JvmField
    val projectRule: CodeInsightTestFixtureRule = JavaCodeInsightTestFixtureRule()

    private val project: Project
        get() = projectRule.project

    private val encoderServer: EncoderServer = mock()
    private lateinit var sut: ProjectContextProvider

    @Before
    fun setup() {
        sut = ProjectContextProvider(project, encoderServer, TestScope())
    }

    @Test
    fun `test index payload is encrypted`() = runTest {
        whenever(encoderServer.port).thenReturn(3000)
        try {
            sut.index()
        } catch (e: ConnectException) {
            // no-op
        }
        verify(encoderServer, times(1)).encrypt(any())
    }

    @Test
    fun `test query payload is encrypted`() = runTest {
        whenever(encoderServer.port).thenReturn(3000)
        try {
            sut.query("what does this project do")
        } catch (e: ConnectException) {
            // no-op
        }
        verify(encoderServer, times(1)).encrypt(any())
    }
}
