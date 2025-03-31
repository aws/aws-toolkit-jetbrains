// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Session

class ChatSessionStorageTest : FeatureDevTestBase() {

    private lateinit var chatSessionStorage: ChatSessionStorage
    private lateinit var mockSession: Session

    @Before
    override fun setup() {
        super.setup()
        chatSessionStorage = ChatSessionStorage()
        mockSession = mock()
    }

    @Test
    fun `check getSession for NewSession`() {
        val testSession = chatSessionStorage.getSession("tabId", project)
        assertThat(testSession).isNotNull()
        assertThat(testSession.tabID).isEqualTo("tabId")
        assertThat(testSession.project).isEqualTo(project)
    }

    @Test
    fun `check getSession for ExistingSession`() {
        whenever(mockSession.tabID).thenReturn("tab1")
        whenever(mockSession.project).thenReturn(projectRule.project)

        val expectedSession = chatSessionStorage.getSession(mockSession.tabID, mockSession.project)
        val actualSession = chatSessionStorage.getSession("tab1", project)
        assertThat(actualSession).isNotNull()
        assertThat(actualSession).isEqualTo(expectedSession)
    }
}
