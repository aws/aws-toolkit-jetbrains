// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.amazonqFeatureDev.session

import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients.FeatureDevClient
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.ConversationNotStartedState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.PrepareRefinementState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Session
import software.aws.toolkits.jetbrains.services.codewhisperer.amazonqFeatureDev.FeatureDevTestBase

class SessionTest : FeatureDevTestBase() {
    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, disposableRule)

    private lateinit var featureDevClient: FeatureDevClient
    private lateinit var session: Session
    private lateinit var messanger: MessagePublisher

    @Before
    override fun setup() {
        featureDevClient = mock()
        session = Session("tabId", projectRule.project)
        session.proxyClient = featureDevClient
        messanger = mock()
    }

    @Test
    fun `test session before preloader`() {
        assertThat(session.sessionState).isInstanceOf(ConversationNotStartedState::class.java)
        assertThat(session.isAuthenticating).isFalse()
    }

    @Test
    fun `test preloader`() = runTest {
        whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)

        session.preloader(userMessage, messanger)
        assertThat(session.conversationId).isEqualTo(testConversationId)
        assertThat(session.sessionState).isInstanceOf(PrepareRefinementState::class.java)
    }
}
