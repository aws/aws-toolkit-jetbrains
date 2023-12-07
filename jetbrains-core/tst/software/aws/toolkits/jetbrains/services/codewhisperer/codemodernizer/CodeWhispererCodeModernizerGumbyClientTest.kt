// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codemodernizer

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhisperer.CodeWhispererClient
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererstreaming.CodeWhispererStreamingAsyncClient
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.MockClientManager
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererEndpointCustomizer

class CodeWhispererCodeModernizerGumbyClientTest {
    val applicationRule = ApplicationRule()
    val mockCredentialManager = MockCredentialManagerRule()
    val mockClientManagerRule = MockClientManagerRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(applicationRule, mockClientManagerRule, mockCredentialManager)

    @Rule
    @JvmField
    val wireMock = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    @Rule
    @JvmField
    val disposable = DisposableRule()

    private lateinit var httpClient: SdkHttpClient
    private lateinit var mockExplorerActionManager: CodeWhispererExplorerActionManager
    private lateinit var codeWhispererRuntimeClient: CodeWhispererRuntimeClient
    private lateinit var codeWhispererStreamingAsyncClient: CodeWhispererStreamingAsyncClient


    @Before
    fun setUp() {
        MockClientManager.useRealImplementations(disposable.disposable)

        codeWhispererRuntimeClient = AwsClientManager.getInstance().createUnmanagedClient(
            mockCredentialManager.createCredentialProvider(),
            Region.US_EAST_1,
            "http://127.0.0.1:${wireMock.port()}"
        )

        codeWhispererStreamingAsyncClient = AwsClientManager.getInstance().createUnmanagedClient(
            mockCredentialManager.createCredentialProvider(),
            Region.US_EAST_1,
            "http://127.0.0.1:${wireMock.port()}"
        )

        wireMock.stubFor(
            WireMock.post("/")
                .willReturn(
                    WireMock.aResponse().withStatus(200)
                )
        )

        mockExplorerActionManager = mock()
        whenever(mockExplorerActionManager.resolveAccessToken()).thenReturn(CodeWhispererTestUtil.testValidAccessToken)
        ApplicationManager.getApplication().replaceService(CodeWhispererExplorerActionManager::class.java, mockExplorerActionManager, disposable.disposable)
    }

    @After
    fun tearDown() {
        tryOrNull { httpClient.close() }
    }

    @Test
    fun `check createUploadUrl request header`() {
        codeWhispererRuntimeClient.createUploadUrl {}
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/"))
                .withHeader(CodeWhispererEndpointCustomizer.TOKEN_KEY_NAME, WireMock.matching(CodeWhispererTestUtil.testValidAccessToken))
                .withHeader(CodeWhispererEndpointCustomizer.OPTOUT_KEY_NAME, WireMock.matching("false"))
        )
    }

    @Test
    fun `check getTransformation request header`() {
        codeWhispererRuntimeClient.getTransformation {}
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/"))
                .withHeader(CodeWhispererEndpointCustomizer.TOKEN_KEY_NAME, WireMock.matching(CodeWhispererTestUtil.testValidAccessToken))
                .withHeader(CodeWhispererEndpointCustomizer.OPTOUT_KEY_NAME, WireMock.matching("false"))
        )
    }

}
