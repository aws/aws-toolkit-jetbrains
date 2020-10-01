// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.InternalErrorException
import software.amazon.awssdk.services.sns.model.SubscribeRequest
import software.amazon.awssdk.services.sns.model.SubscribeResponse
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider

class SubscribeSnsDialogTest {
    lateinit var snsClient: SnsClient
    lateinit var region: AwsRegion
    lateinit var queue: Queue

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule()

    @Before
    fun setup() {
        snsClient = mockClientManagerRule.create()
        region = MockRegionProvider.getInstance().defaultRegion()
        queue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/test", region)
    }

    @Test
    fun `No topic specified fails`() {
        runInEdtAndWait {
            val dialog = SubscribeSnsDialog(projectRule.project, queue)
            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Error subscribing to topic`() {
        val subscribeCaptor = argumentCaptor<SubscribeRequest>()
        snsClient.stub {
            on { subscribe(subscribeCaptor.capture()) } doThrow InternalErrorException.builder().message(ERROR_MESSAGE).build()
        }

        runInEdtAndWait {
            val dialog = SubscribeSnsDialog(projectRule.project, queue)
            assertThatThrownBy { dialog.subscribe(TOPIC_ARN) }.hasMessage(ERROR_MESSAGE)
        }
        assertThat(subscribeCaptor.firstValue.topicArn()).isEqualTo(TOPIC_ARN)
    }

    @Test
    fun `Subscribing to topic succeeds`() {
        val subscribeCaptor = argumentCaptor<SubscribeRequest>()
        snsClient.stub {
            on { subscribe(subscribeCaptor.capture()) } doReturn SubscribeResponse.builder().build()
        }

        runInEdtAndWait {
            SubscribeSnsDialog(projectRule.project, queue).subscribe(TOPIC_ARN)
        }
        assertThat(subscribeCaptor.firstValue.topicArn()).isEqualTo(TOPIC_ARN)
    }

    private companion object {
        const val TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:MyTopic"
        const val ERROR_MESSAGE = "Network Error"
    }
}
