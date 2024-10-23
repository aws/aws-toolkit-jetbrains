// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.workspace.context

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.services.amazonq.project.EncoderServer
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextController
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings

class ProjectContextControllerTest {
    lateinit var sut: ProjectContextController

    val project: Project
        get() = projectExtension.project

    private companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @Test
    fun `should start encoderServer if chat project context is disabled`(@TestDisposable disposable: Disposable) = runTest {
        ApplicationManager.getApplication()
            .replaceService(
                CodeWhispererSettings::class.java,
                mock { on { isProjectContextEnabled() } doReturn false },
                disposable
            )

        assertEncoderServerStarted()
    }

    @Test
    fun `should start encoderServer if chat project context is enabled`(@TestDisposable disposable: Disposable) = runTest {
        ApplicationManager.getApplication()
            .replaceService(
                CodeWhispererSettings::class.java,
                mock { on { isProjectContextEnabled() } doReturn true },
                disposable
            )

        assertEncoderServerStarted()
    }

    private fun assertEncoderServerStarted() = runTest {
        mockConstruction(EncoderServer::class.java).use {
            // TODO: figure out how to make this testScope work
//            val cs = TestScope(context = StandardTestDispatcher()) // not works and the test never finish
            val cs = CoroutineScope(getCoroutineBgContext()) // works

            assertThat(it.constructed()).isEmpty()
            sut = ProjectContextController(project, cs)
            assertThat(it.constructed()).hasSize(1)

//            cs.advanceUntilIdle()
            sut.initJob.join()
            val encoderServer = it.constructed().first()
            verify(encoderServer, times(1)).downloadArtifactsAndStartServer()
        }
    }
}
