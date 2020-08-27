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
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider

class EditAttributesDialogTest {
    lateinit var client: SqsClient
    lateinit var region: AwsRegion
    lateinit var queue: Queue

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    @Before
    fun setUp() {
        client = mockClientManagerRule.create()
        region = MockRegionProvider.getInstance().defaultRegion()
        queue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/test", region)
    }

    @Test
    fun `Empty field fails`() {
        runInEdtAndWait {
            val dialog = EditAttributesDialog(projectRule.project, client, queue).apply {
                view.deliveryDelay.text = ""
            }
            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Value out of bound fails`() {
        runInEdtAndWait {
            val dialog = EditAttributesDialog(projectRule.project, client, queue).apply {
                view.deliveryDelay.text = (MAX_DELIVERY_DELAY + 1).toString()
            }
            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Editing queue attributes succeeds`() {
        val attributesCaptor = argumentCaptor<SetQueueAttributesRequest>()
        client.stub {
            on { setQueueAttributes(attributesCaptor.capture()) } doReturn SetQueueAttributesResponse.builder().build()
        }

        runInEdtAndWait {
            EditAttributesDialog(projectRule.project, client, queue).apply {
                view.visibilityTimeout.text = TEST_VISIBILITY_TIMEOUT
                view.messageSize.text = TEST_MESSAGE_SIZE
                view.retentionPeriod.text = TEST_RETENTION_PERIOD
                view.deliveryDelay.text = TEST_DELAY_SECONDS
                view.waitTime.text = TEST_WAIT_TIME
                updateAttributes()
            }
        }

        val updatedAttributes = attributesCaptor.firstValue.attributes()
        assertThat(updatedAttributes[QueueAttributeName.VISIBILITY_TIMEOUT]).isEqualTo(TEST_VISIBILITY_TIMEOUT)
        assertThat(updatedAttributes[QueueAttributeName.MAXIMUM_MESSAGE_SIZE]).isEqualTo(TEST_MESSAGE_SIZE)
        assertThat(updatedAttributes[QueueAttributeName.MESSAGE_RETENTION_PERIOD]).isEqualTo(TEST_RETENTION_PERIOD)
        assertThat(updatedAttributes[QueueAttributeName.DELAY_SECONDS]).isEqualTo(TEST_DELAY_SECONDS)
        assertThat(updatedAttributes[QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS]).isEqualTo(TEST_WAIT_TIME)
    }

    @Test
    fun `Error editing queue attributes`() {
        val attributesCaptor = argumentCaptor<SetQueueAttributesRequest>()
        client.stub {
            on { setQueueAttributes(attributesCaptor.capture()) } doThrow AwsServiceException.builder().message(ERROR_MESSAGE).build()
        }

        runInEdtAndWait {
            val dialog = EditAttributesDialog(projectRule.project, client, queue).apply {
                view.visibilityTimeout.text = TEST_VISIBILITY_TIMEOUT
                view.messageSize.text = TEST_MESSAGE_SIZE
                view.retentionPeriod.text = TEST_RETENTION_PERIOD
                view.deliveryDelay.text = TEST_DELAY_SECONDS
                view.waitTime.text = TEST_WAIT_TIME
            }
            assertThatThrownBy { dialog.updateAttributes() }.hasMessage(ERROR_MESSAGE)
        }

        val updatedAttributes = attributesCaptor.firstValue.attributes()
        assertThat(updatedAttributes[QueueAttributeName.VISIBILITY_TIMEOUT]).isEqualTo(TEST_VISIBILITY_TIMEOUT)
        assertThat(updatedAttributes[QueueAttributeName.MAXIMUM_MESSAGE_SIZE]).isEqualTo(TEST_MESSAGE_SIZE)
        assertThat(updatedAttributes[QueueAttributeName.MESSAGE_RETENTION_PERIOD]).isEqualTo(TEST_RETENTION_PERIOD)
        assertThat(updatedAttributes[QueueAttributeName.DELAY_SECONDS]).isEqualTo(TEST_DELAY_SECONDS)
        assertThat(updatedAttributes[QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS]).isEqualTo(TEST_WAIT_TIME)
    }

    private companion object {
        const val TEST_VISIBILITY_TIMEOUT = MAX_VISIBILITY_TIMEOUT.toString()
        const val TEST_MESSAGE_SIZE = MAX_MESSAGE_SIZE_LIMIT.toString()
        const val TEST_RETENTION_PERIOD = MAX_RETENTION_PERIOD.toString()
        const val TEST_DELAY_SECONDS = MAX_DELIVERY_DELAY.toString()
        const val TEST_WAIT_TIME = MAX_WAIT_TIME.toString()
        const val ERROR_MESSAGE = "Internal error"
    }
}
