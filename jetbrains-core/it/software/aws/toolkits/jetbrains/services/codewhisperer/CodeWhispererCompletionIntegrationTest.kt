// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.cppFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.utils.rules.RunWithRealCredentials.RequiresRealCredentials

@RequiresRealCredentials
class CodeWhispererCompletionIntegrationTest : CodeWhispererIntegrationTestBase() {

    @Before
    override fun setUp() {
        super.setUp()
        scanManager = spy(CodeWhispererCodeScanManager.getInstance(projectRule.project))
        doNothing().whenever(scanManager).addCodeScanUI(any())
        projectRule.project.replaceService(CodeWhispererCodeScanManager::class.java, scanManager, disposableRule.disposable)
    }

    @Test
    fun testInvokeCompletionManualTrigger() {
        assertDoesNotThrow {
            withCodeWhispererServiceInvokedAndWait { response ->
                val requestId = response.responseMetadata().requestId()
                assertThat(requestId).isNotNull
                val sessionId = response.sdkHttpResponse().headers().getOrDefault(
                    CodeWhispererService.KET_SESSION_ID,
                    listOf(requestId)
                )[0]
                assertThat(sessionId).isNotNull
            }
        }
    }

    @Test
    fun testInvokeCompletionAutoTrigger() {
        assertDoesNotThrow {
            stateManager.setAutoEnabled(true)
            withCodeWhispererServiceInvokedAndWait(false) { response ->
                val requestId = response.responseMetadata().requestId()
                assertThat(requestId).isNotNull
                val sessionId = response.sdkHttpResponse().headers().getOrDefault(
                    CodeWhispererService.KET_SESSION_ID,
                    listOf(requestId)
                )[0]
                assertThat(sessionId).isNotNull
            }
        }
    }

    @Test
    fun testInvokeCompletionUnsupportedLanguage() {
        setFileContext(cppFileName, CodeWhispererTestUtil.cppTestLeftContext, "")
        assertDoesNotThrow {
            invokeCodeWhispererService()
            verify(popupManager, never()).showPopup(any(), any(), any(), any(), any())
            verify(clientAdaptor, never()).listRecommendationsPaginator(any(), any())
        }
    }
}
