// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ReauthSource
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule

class CodeWhispererUtilTest : HeavyPlatformTestCase() {
    private val mockRegionProviderExtension = MockRegionProviderRule()

    override fun setUp() {
        super.setUp()
        mockRegionProviderExtension.apply(object : org.junit.runners.model.Statement() {
            override fun evaluate() {}
        }, org.junit.runner.Description.EMPTY).evaluate()
    }

    fun testReconnectCodeWhispererRespectsConnectionSettings() {
        mockkStatic(::reauthConnectionIfNeeded)
        val mockConnectionManager = mockk<ToolkitConnectionManager>(relaxed = true)
        val mockConnection = mockk<ManagedBearerSsoConnection>()
        project.replaceService(ToolkitConnectionManager::class.java, mockConnectionManager, testRootDisposable)
        ApplicationManager.getApplication().replaceService(ToolkitAuthManager::class.java, mockk(relaxed = true), testRootDisposable)
        val startUrl = aString()
        val region = mockRegionProviderExtension.createAwsRegion().id
        val scopes = listOf(aString(), aString())

        every { mockConnectionManager.activeConnectionForFeature(any()) } returns mockConnection
        every { mockConnection.startUrl } returns startUrl
        every { mockConnection.region } returns region
        every { mockConnection.scopes } returns scopes

        CodeWhispererUtil.reconnectCodeWhisperer(project)

        verify {
            reauthConnectionIfNeeded(project, mockConnection, isReAuth = true, reauthSource = ReauthSource.CODEWHISPERER)
        }
    }
}
