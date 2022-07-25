// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.spy
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.codewhisperer.CodeWhispererClient
import software.amazon.awssdk.services.codewhisperer.model.GetAccessTokenRequest
import software.amazon.awssdk.services.codewhisperer.model.GetAccessTokenResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.DevToolsToolWindow
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreStateType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import java.util.concurrent.atomic.AtomicInteger

class CodeWhispererExplorerActionManagerTest {
    @Rule
    @JvmField
    var projectRule = PythonCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val mockClientManagerRule = MockClientManagerRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private lateinit var project: Project
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var explorerManager: CodeWhispererExplorerActionManager
    private lateinit var clientMock: CodeWhispererClient

    @Before
    fun setup() {
        project = projectRule.project
        fixture = projectRule.fixture
        clientMock = mockClientManagerRule.create()
        explorerManager = spy(CodeWhispererExplorerActionManager())

        project.replaceService(CodeWhispererCodeScanManager::class.java, mock(), disposableRule.disposable)
    }
    @Test
    fun `test getCodeWhispererExplorerState() -- default value`() {
        val state = explorerManager.getExplorerState()
        assertThat(state.value.size).isEqualTo(0)
        assertThat(state.token).isNull()

        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAuthorized)).isFalse
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled)).isFalse
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsManualEnabled)).isFalse
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.HasAcceptedTermsOfServices)).isFalse

        state.value[CodeWhispererExploreStateType.IsAutoEnabled] = true
        state.token = "foo"

        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled)).isTrue
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAuthorized)).isTrue
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsManualEnabled)).isFalse
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.HasAcceptedTermsOfServices)).isFalse
    }

    @Test
    fun `test setCodeWhispererExplorerState() - IsAutoEnabled`() {
        val state = explorerManager.getExplorerState()
        assertThat(state.value.size).isEqualTo(0)
        assertThat(state.token).isNull()
        val counter = AtomicInteger(0)
        val runnable: () -> Unit = { counter.incrementAndGet() }

        // update succeed
        var oldStamp = state.modificationCount
        var oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled, true, runnable)
        assertThat(counter.get()).isEqualTo(oldCount + 1)
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled)).isTrue
        assertThat(state.modificationCount).isEqualTo(oldStamp + 1)

        // update fail, neither counter nor modificationCount will increment
        oldStamp = state.modificationCount
        oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled, true, runnable)
        assertThat(counter.get()).isEqualTo(oldCount)
        assertThat(state.modificationCount).isEqualTo(oldStamp)
    }

    @Test
    fun `test setCodeWhispererExplorerState() - IsManualEnabled`() {
        val state = explorerManager.getExplorerState()
        assertThat(state.value.size).isEqualTo(0)
        assertThat(state.token).isNull()
        val counter = AtomicInteger(0)
        val runnable: () -> Unit = { counter.incrementAndGet() }

        // update succeed
        var oldStamp = state.modificationCount
        var oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsManualEnabled, true, runnable)
        assertThat(counter.get()).isEqualTo(oldCount + 1)
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsManualEnabled)).isTrue
        assertThat(state.modificationCount).isEqualTo(oldStamp + 1)

        // update fail, neither counter nor modificationCount will increment
        oldStamp = state.modificationCount
        oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsManualEnabled, true, runnable)
        assertThat(counter.get()).isEqualTo(oldCount)
        assertThat(state.modificationCount).isEqualTo(oldStamp)
    }

    @Test
    fun `test setCodeWhispererExplorerState() - IsAuthorized`() {
        val state = explorerManager.getExplorerState()
        assertThat(state.token).isNull()
        val counter = AtomicInteger(0)
        val runnable: () -> Unit = { counter.incrementAndGet() }

        // update succeed
        var oldStamp = state.modificationCount
        var oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAuthorized, "foo", runnable)
        assertThat(counter.get()).isEqualTo(oldCount + 1)
        assertThat(state.token).isEqualTo("foo")
        assertThat(state.modificationCount).isEqualTo(oldStamp + 1)

        // update fail, neither counter nor modificationCount will increment
        oldStamp = state.modificationCount
        oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAuthorized, "foo", runnable)
        assertThat(counter.get()).isEqualTo(oldCount)
        assertThat(state.modificationCount).isEqualTo(oldStamp)
    }

    @Test
    fun `test setCodeWhispererExplorerState() - HasAcceptedTermsOfServices`() {
        val state = explorerManager.getExplorerState()
        assertThat(state.value.size).isEqualTo(0)
        assertThat(state.token).isNull()
        val counter = AtomicInteger(0)
        val runnable: () -> Unit = { counter.incrementAndGet() }

        // update succeed
        var oldStamp = state.modificationCount
        var oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.HasAcceptedTermsOfServices, true, runnable)
        assertThat(counter.get()).isEqualTo(oldCount + 1)
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.HasAcceptedTermsOfServices)).isTrue
        assertThat(state.modificationCount).isEqualTo(oldStamp + 1)

        // update fail, neither counter nor modificationCount will increment
        oldStamp = state.modificationCount
        oldCount = counter.get()
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.HasAcceptedTermsOfServices, true, runnable)
        assertThat(counter.get()).isEqualTo(oldCount)
        assertThat(state.modificationCount).isEqualTo(oldStamp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test setCodeWhispererExplorerState() - wrong argument type is passed`() {
        val state = explorerManager.getExplorerState()
        assertThat(state.value.size).isEqualTo(0)
        assertThat(state.token).isNull()
        val counter = AtomicInteger(0)
        val runnable: () -> Unit = { counter.incrementAndGet() }

        var oldStamp = state.modificationCount
        val oldCount = counter.get()
        // Boolean is required, thus update should fail and neither modificationCount nor counter will increment
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled, "true", runnable)
        assertThat(explorerManager.getCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAutoEnabled)).isFalse
        assertThat(state.modificationCount).isEqualTo(oldStamp)
        assertThat(counter.get()).isEqualTo(oldCount)

        oldStamp = state.modificationCount
        // String is required, thus update should fail and neither modificationCount nor counter will increment
        explorerManager.setCodeWhispererExplorerState(CodeWhispererExploreStateType.IsAuthorized, 123, runnable)
        assertThat(state.token).isNull()
        assertThat(state.modificationCount).isEqualTo(oldStamp)
        assertThat(counter.get()).isEqualTo(oldCount)
    }

    @Test
    fun `test performAction() -- ACTION_WHAT_IS_CODEWHISPERER`() {
        val devToolsToolWindowMock: DevToolsToolWindow = mock()
        project.replaceService(DevToolsToolWindow::class.java, devToolsToolWindowMock, disposableRule.disposable)
        doNothing().`when`(explorerManager).showWhatIsCodeWhisperer()

        explorerManager.performAction(project, CodeWhispererExplorerActionManager.ACTION_WHAT_IS_CODEWHISPERER)
        verify(explorerManager, Times(1)).showWhatIsCodeWhisperer()
    }

    @Test
    fun `test performAction() -- ACTION_PAUSE_CODEWHISPERER`() {
        val devToolsToolWindowMock: DevToolsToolWindow = mock()
        project.replaceService(DevToolsToolWindow::class.java, devToolsToolWindowMock, disposableRule.disposable)

        explorerManager.performAction(project, CodeWhispererExplorerActionManager.ACTION_PAUSE_CODEWHISPERER)
        verify(explorerManager, Times(1)).setCodeWhispererExplorerState(eq(CodeWhispererExploreStateType.IsAutoEnabled), eq(false), any())
        verify(devToolsToolWindowMock, Times(1)).redrawTree()
    }

    @Test
    fun `test performAction() -- ACTION_RESUME_CODEWHISPERER`() {
        val devToolsToolWindowMock: DevToolsToolWindow = mock()
        project.replaceService(DevToolsToolWindow::class.java, devToolsToolWindowMock, disposableRule.disposable)

        explorerManager.performAction(project, CodeWhispererExplorerActionManager.ACTION_RESUME_CODEWHISPERER)
        verify(explorerManager, Times(1)).setCodeWhispererExplorerState(eq(CodeWhispererExploreStateType.IsAutoEnabled), eq(true), any())
        verify(devToolsToolWindowMock, Times(1)).redrawTree()
    }

    @Test
    fun `test performAction() -- ACTION_OPEN_CODE_REFERENCE_PANEL`() {
        val codeWhispererCodeReferenceManagerMock: CodeWhispererCodeReferenceManager = mock()
        project.replaceService(CodeWhispererCodeReferenceManager::class.java, codeWhispererCodeReferenceManagerMock, disposableRule.disposable)

        explorerManager.performAction(project, CodeWhispererExplorerActionManager.ACTION_OPEN_CODE_REFERENCE_PANEL)
        verify(codeWhispererCodeReferenceManagerMock, Times(1)).showCodeReferencePanel()
    }

    @Test
    fun `test performAction() -- ACTION_RUN_SECURITY_SCAN`() {
        val codeWhispererCodeScanManagerMock: CodeWhispererCodeScanManager = mock()
        project.replaceService(CodeWhispererCodeScanManager::class.java, codeWhispererCodeScanManagerMock, disposableRule.disposable)

        explorerManager.performAction(project, CodeWhispererExplorerActionManager.ACTION_RUN_SECURITY_SCAN)
        verify(codeWhispererCodeScanManagerMock, Times(1)).runCodeScan()
    }

    @Test
    fun `test getNewAccessTokenAndPersist()`() {
        val clientManagerMock: CodeWhispererClientManager = mock()
        val tokenCaptor = argumentCaptor<GetAccessTokenRequest>()
        whenever(clientManagerMock.getClient()).thenReturn(clientMock)
        whenever(clientMock.getAccessToken(any<GetAccessTokenRequest>())).thenReturn(GetAccessTokenResponse.builder().accessToken("bar").build())
        doNothing().`when`(explorerManager).setCodeWhispererExplorerState(any(), any(), any())

        explorerManager.getNewAccessTokenAndPersist("foo")
        verify(clientMock, Times(1)).getAccessToken(tokenCaptor.capture())
        assertThat(tokenCaptor.firstValue.identityToken()).isEqualTo("foo")
        verify(explorerManager, Times(1)).setCodeWhispererExplorerState(eq(CodeWhispererExploreStateType.IsAuthorized), eq("bar"), any())
    }
}
