// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.ManagedSsoProfile
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.credentials.MockToolkitAuthManagerRule
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQServerInstanceFacade
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQServerInstanceStarter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsExtendedInitializeResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.WorkspaceInfo
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.codeWhispererRecommendationActionId
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonTestLeftContext
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.testValidAccessToken
import software.aws.toolkits.jetbrains.services.codewhisperer.actions.CodeWhispererRecommendationAction
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreActionState
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreStateType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererRecommendationManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_DIM_HEX
import software.aws.toolkits.jetbrains.settings.CodeWhispererConfiguration
import software.aws.toolkits.jetbrains.settings.CodeWhispererConfigurationType
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.resources.message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

// TODO: restructure testbase, too bulky and hard to debug
open class CodeWhispererTestBase {
    var projectRule = PythonCodeInsightTestFixtureRule()
    val mockClientManagerRule = MockClientManagerRule()
    val mockCredentialRule = MockCredentialManagerRule()
    val disposableRule = DisposableRule()
    val authManagerRule = MockToolkitAuthManagerRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, mockCredentialRule, mockClientManagerRule, authManagerRule, disposableRule)

    protected lateinit var mockLspService: AmazonQLspService
    protected lateinit var mockLanguageServer: AmazonQLanguageServer

    protected lateinit var popupManagerSpy: CodeWhispererPopupManager
    protected lateinit var clientAdaptorSpy: CodeWhispererClientAdaptor
    internal lateinit var stateManager: CodeWhispererExplorerActionManager
    protected lateinit var recommendationManager: CodeWhispererRecommendationManager
    protected lateinit var codewhispererService: CodeWhispererService
    protected lateinit var editorManager: CodeWhispererEditorManager
    protected lateinit var settingsManager: CodeWhispererSettings
    private lateinit var originalExplorerActionState: CodeWhispererExploreActionState
    private lateinit var originalSettings: CodeWhispererConfiguration
    private lateinit var qRegionProfileManagerSpy: QRegionProfileManager
    protected lateinit var codeScanManager: CodeWhispererCodeScanManager

    @Before
    open fun setUp() = runTest {
        mockLanguageServer = mockk()
        VfsRootAccess.allowRootAccess(disposableRule.disposable, "/usr/bin", "/usr/local/bin", "C:/Program Files/pypy3.10-v7.3.17-win64")
        val starter = object : AmazonQServerInstanceStarter {
            override fun start(
                project: Project,
                cs: CoroutineScope,
            ): AmazonQServerInstanceFacade = object : AmazonQServerInstanceFacade {
                override val launcher: Launcher<AmazonQLanguageServer>
                    get() = TODO("Not yet implemented")

                @Suppress("ForbiddenVoid")
                override val launcherFuture: Future<Void>
                    get() = CompletableFuture()

                override val initializeResult: Deferred<AwsExtendedInitializeResult>
                    get() = CompletableDeferred(AwsExtendedInitializeResult(mockk()))

                override val encryptionManager: JwtEncryptionManager
                    get() = TODO("Not yet implemented")

                override val languageServer: AmazonQLanguageServer
                    get() = mockLanguageServer

                override val rawEndpoint: RemoteEndpoint
                    get() = TODO("Not yet implemented")

                override fun dispose() {}
            }
        }

        mockLspService = spy(AmazonQLspService(starter, projectRule.project, this))

        // Mock the service methods on Project
        projectRule.project.replaceService(AmazonQLspService::class.java, mockLspService, disposableRule.disposable)
        // wait for init to finish
        mockLspService.instanceFlow.first()

        mockLspInlineCompletionResponse(pythonResponse)

        mockClientManagerRule.create<SsoOidcClient>()
        every { mockLanguageServer.logInlineCompletionSessionResults(any()) } returns Unit

        popupManagerSpy = spy(CodeWhispererPopupManager.getInstance())
        popupManagerSpy.reset()
        doNothing().whenever(popupManagerSpy).showPopup(any(), any(), any(), any())
        popupManagerSpy.stub {
            onGeneric {
                showPopup(any(), any(), any(), any())
            } doAnswer {
                CodeWhispererInvocationStatus.getInstance().setDisplaySessionActive(true)
            }
        }
        ApplicationManager.getApplication().replaceService(CodeWhispererPopupManager::class.java, popupManagerSpy, disposableRule.disposable)

        stateManager = spy(CodeWhispererExplorerActionManager.getInstance())
        recommendationManager = CodeWhispererRecommendationManager.getInstance()
        // Use mock with CALLS_REAL_METHODS instead of spy to avoid invoking real suspend method during stub setup
        codewhispererService = mock<CodeWhispererService>(defaultAnswer = Answers.CALLS_REAL_METHODS) {
            onBlocking {
                getWorkspaceIds(any())
            } doSuspendableAnswer {
                CompletableFuture.completedFuture(LspServerConfigurations(listOf(WorkspaceInfo("file:///", "workspaceId"))))
            }
        }
        ApplicationManager.getApplication().replaceService(CodeWhispererService::class.java, codewhispererService, disposableRule.disposable)
        editorManager = CodeWhispererEditorManager.getInstance()
        settingsManager = CodeWhispererSettings.getInstance()

        setFileContext(pythonFileName, pythonTestLeftContext, "")

        originalExplorerActionState = stateManager.state
        originalSettings = settingsManager.state
        stateManager.loadState(
            CodeWhispererExploreActionState().apply {
                CodeWhispererExploreStateType.values().forEach {
                    value[it] = true
                }
                token = testValidAccessToken
            }
        )
        stateManager.stub {
            onGeneric {
                checkActiveCodeWhispererConnectionType(any())
            } doAnswer {
                CodeWhispererLoginType.Sono
            }
        }
        settingsManager.loadState(
            CodeWhispererConfiguration().apply {
                value[CodeWhispererConfigurationType.IsIncludeCodeWithReference] = true
            }
        )

        clientAdaptorSpy = spy(CodeWhispererClientAdaptor.getInstance(projectRule.project))
        projectRule.project.replaceService(CodeWhispererClientAdaptor::class.java, clientAdaptorSpy, disposableRule.disposable)
        ApplicationManager.getApplication().replaceService(CodeWhispererExplorerActionManager::class.java, stateManager, disposableRule.disposable)
        stateManager.setAutoEnabled(false)

        codeScanManager = spy(CodeWhispererCodeScanManager.getInstance(projectRule.project))
        doNothing().`when`(codeScanManager).buildCodeScanUI()
        doNothing().`when`(codeScanManager).removeCodeScanUI()
        projectRule.project.replaceService(CodeWhispererCodeScanManager::class.java, codeScanManager, disposableRule.disposable)

        val conn = authManagerRule.createConnection(ManagedSsoProfile("us-east-1", "url", Q_SCOPES))
        ToolkitConnectionManager.getInstance(projectRule.project).switchConnection(conn)

        qRegionProfileManagerSpy = spy(QRegionProfileManager.getInstance())
        qRegionProfileManagerSpy.stub {
            onGeneric {
                hasValidConnectionButNoActiveProfile(any())
            } doAnswer {
                false
            }
        }
        ApplicationManager.getApplication().replaceService(QRegionProfileManager::class.java, qRegionProfileManagerSpy, disposableRule.disposable)
    }

    @After
    open fun tearDown() {
        stateManager.loadState(originalExplorerActionState)
        settingsManager.loadState(originalSettings)
        popupManagerSpy.reset()
        runInEdtAndWait {
            popupManagerSpy.closePopup()
        }

        Disposer.dispose(mockLspService)
    }

    fun withCodeWhispererServiceInvokedAndWait(runnable: (InvocationContext) -> Unit) {
        val statesCaptor = argumentCaptor<InvocationContext>()
        invokeCodeWhispererService()
        verify(popupManagerSpy, timeout(5000).atLeastOnce())
            .showPopup(statesCaptor.capture(), any(), any(), any())
        val states = statesCaptor.lastValue

        runInEdtAndWait {
            try {
                runnable(states)
            } finally {
                CodeWhispererPopupManager.getInstance().closePopup(states.popup)
            }
        }

        // To make sure the previous runnable on EDT thread is complete before the test proceeds
        runInEdtAndWait {}
    }

    /**
     * Block until manual action has either failed or completed
     */
    fun invokeCodeWhispererService() {
        val jobRef = AtomicReference<Job?>()
        runInEdtAndWait {
            projectRule.fixture.editor.putUserData(CodeWhispererRecommendationAction.ACTION_JOB_KEY, jobRef)
            // does not block, so we need to extract something to track the async task
            projectRule.fixture.performEditorAction(codeWhispererRecommendationActionId)
        }

        runTest {
            // wait for CodeWhispererService#showRecommendationsInPopup to complete, if started
            jobRef.get()?.join()

            // wait for subsequent background operations to be complete
            while (CodeWhispererInvocationStatus.getInstance().hasExistingServiceInvocation()) {
                yield()
                delay(10)
            }
        }
    }

    fun checkRecommendationInfoLabelText(selected: Int, total: Int) {
        // should show "[selected] of [total]"
        val actual = popupManagerSpy.popupComponents.recommendationInfoLabel.text
        val expected = message("codewhisperer.popup.recommendation_info", selected, total, POPUP_DIM_HEX)
        assertThat(actual).isEqualTo(expected)
    }

    fun setFileContext(filename: String, leftContext: String, rightContext: String) {
        projectRule.fixture.configureByText(filename, leftContext + rightContext)
        runInEdtAndWait {
            projectRule.fixture.editor.caretModel.primaryCaret.moveToOffset(leftContext.length)
        }
    }

    fun mockCodeWhispererEnabledStatus(enabled: Boolean) {
        stateManager.stub {
            onGeneric {
                checkActiveCodeWhispererConnectionType(any())
            } doAnswer {
                if (enabled) CodeWhispererLoginType.Sono else CodeWhispererLoginType.Logout
            }
        }
    }

    fun addUserInputAfterInvocation(userInput: String) {
        val triggerTypeCaptor = argumentCaptor<TriggerTypeInfo>()
        val editorCaptor = argumentCaptor<Editor>()
        val projectCaptor = argumentCaptor<Project>()
        val psiFileCaptor = argumentCaptor<PsiFile>()
        val latencyContextCaptor = argumentCaptor<LatencyContext>()

        codewhispererService.stub {
            onBlocking {
                getRequestContext(
                    triggerTypeCaptor.capture(),
                    editorCaptor.capture(),
                    projectCaptor.capture(),
                    psiFileCaptor.capture(),
                    latencyContextCaptor.capture()
                )
            } doSuspendableAnswer {
                val requestContext = codewhispererService.getRequestContext(
                    triggerTypeCaptor.firstValue,
                    editorCaptor.firstValue,
                    projectRule.project,
                    psiFileCaptor.firstValue,
                    latencyContextCaptor.firstValue
                )
                projectRule.fixture.type(userInput)
                requestContext
            }
        }
    }

    fun mockLspInlineCompletionResponse(response: InlineCompletionListWithReferences) {
        every { mockLanguageServer.inlineCompletionWithReferences(any()) } returns CompletableFuture.completedFuture(response)
    }
}
