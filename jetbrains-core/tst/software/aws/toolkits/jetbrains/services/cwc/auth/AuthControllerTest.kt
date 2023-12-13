// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.auth

import com.intellij.openapi.project.Project
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.ActiveConnection
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.checkBearerConnectionValidity
import software.aws.toolkits.jetbrains.core.gettingstarted.reauthenticateWithQ
import software.aws.toolkits.jetbrains.core.gettingstarted.requestCredentialsForQ
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.MatchPolicy
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.utility.EdtUtility
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.telemetry.CwsprChatCommandType
import software.aws.toolkits.telemetry.UiTelemetry
import java.time.Instant

class AuthControllerTest {

    private val mockProject : Project = mockk<Project>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("software.aws.toolkits.jetbrains.core.gettingstarted.editor.GettingStartedPanelUtilsKt")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `NotConnected returns AuthNeededState`() {
        // Arrange
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.Q) } returns ActiveConnection.NotConnected
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.CODEWHISPERER) } returns ActiveConnection.NotConnected

        // Act
        val authController = AuthController()
        val authNeededState = authController.getAuthNeededState(mockProject)

        // Assert
        assertThat(authNeededState).isNotNull
        assertThat(authNeededState?.authType).isEqualTo(AuthFollowUpType.FullAuth)
    }

    @Test
    fun `NotConnected with connected CodeWhisperer returns MissingScope`(){
        // Arrange
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.Q) } returns ActiveConnection.NotConnected
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.CODEWHISPERER) } returns mockk<ActiveConnection.ValidBearer>(relaxed = true)

        // Act
        val authController = AuthController()
        val authNeededState = authController.getAuthNeededState(mockProject)

        // Assert
        assertThat(authNeededState).isNotNull
        assertThat(authNeededState?.authType).isEqualTo(AuthFollowUpType.MissingScopes)
    }

    @Test
    fun `ValidBearer returns null`() {
        // Arrange
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.Q) } returns mockk<ActiveConnection.ValidBearer>(relaxed = true)
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.CODEWHISPERER) } returns ActiveConnection.NotConnected

        // Act
        val authController = AuthController()
        val authNeededState = authController.getAuthNeededState(mockProject)

        // Assert
        assertThat(authNeededState).isNull()
    }

    @Test
    fun `ExpiredBearer returns AuthNeededState`() {
        // Arrange
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.Q) } returns mockk<ActiveConnection.ExpiredBearer>(relaxed = true)
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.CODEWHISPERER) } returns ActiveConnection.NotConnected

        // Act
        val authController = AuthController()
        val authNeededState = authController.getAuthNeededState(mockProject)

        // Assert
        assertThat(authNeededState).isNotNull
        assertThat(authNeededState?.authType).isEqualTo(AuthFollowUpType.ReAuth)
    }

    @Test
    fun `ExpiredIam returns AuthNeededState`() {
        // Arrange
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.Q) } returns mockk<ActiveConnection.ExpiredIam>(relaxed = true)
        every { checkBearerConnectionValidity(any(), BearerTokenFeatureSet.CODEWHISPERER) } returns ActiveConnection.NotConnected

        // Act
        val authController = AuthController()
        val authNeededState = authController.getAuthNeededState(mockProject)

        // Assert
        assertThat(authNeededState).isNotNull
        assertThat(authNeededState?.authType).isEqualTo(AuthFollowUpType.FullAuth)
    }

    /*
    // Broken due to Kotlin glitch preventing mocking of outside method "GettingStartedAuthUtilsKt"
    @Test
    fun `handleAuth with MissingScopes calls requestCredentialsForQ`() {
        // Arrange
        mockkObject(TelemetryService)
        every {
            TelemetryService.getInstance().record(any<Project>(), any())
        } returns Unit

        // Broken here: cannot mock outside method
        mockkStatic("software.aws.toolkits.jetbrains.core.gettingstarted.GettingStartedAuthUtilsKt")
        every {
            requestCredentialsForQ(
                any(), // project
                any(), // initialConnectionCount, using any() since we can't replicate default logic
                any(), // initialAuthConnections, using any() for the same reason
                any(), // isFirstInstance
                any(), // connectionInitiatedFromExplorer
                eq(true) // connectionInitiatedFromQChatPanel, with specific value
            )
        } returns true

        mockkObject(TelemetryHelper)
        every { TelemetryHelper.recordTelemetryChatRunCommand(any(), any()) } returns Unit

        // computeInEdt
        mockkObject(EdtUtility)
        every { EdtUtility.runInEdt(any()) } answers {
            firstArg<() -> Unit>().invoke()
        }

        // Act
        val testAuthFollowupType = AuthFollowUpType.MissingScopes
        val authController = AuthController()
        authController.handleAuth(mockProject, testAuthFollowupType)

        // Assert
        // Cannot capture: verify { UiTelemetry.click(mockProject, "amazonq_chatAuthenticate") }
        coVerify { runBlocking { requestCredentialsForQ(mockProject, connectionInitiatedFromQChatPanel = true) } }

        verify { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Auth, testAuthFollowupType.name) }
    }
     */

    @Test
    fun `handleAuth with ReAuth calls reauthenticateWithQ`() {
        // Arrange
        mockkStatic("software.aws.toolkits.jetbrains.core.gettingstarted.GettingStartedAuthUtilsKt")
        every {  reauthenticateWithQ(any())  } returns Unit

        mockkObject(TelemetryHelper)
        every { TelemetryHelper.recordTelemetryChatRunCommand(any(), any()) } returns Unit

        // computeInEdt
        mockkObject(EdtUtility)
        every { EdtUtility.runInEdt(any()) } answers {
            firstArg<() -> Unit>().invoke()
        }

        // Act
        val testAuthFollowupType = AuthFollowUpType.ReAuth
        val authController = AuthController()
        authController.handleAuth(mockProject, testAuthFollowupType)

        // Assert
        coVerify { runBlocking { reauthenticateWithQ(mockProject) } }

        verify { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Auth, testAuthFollowupType.name) }
    }

}
