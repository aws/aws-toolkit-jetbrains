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
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException
import software.aws.toolkits.jetbrains.core.MockClientManagerRule

class CreateQueueDialogTest {
    lateinit var client: SqsClient

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    @Before
    fun setup() {
        client = mockClientManagerRule.create()
    }

    @Test
    fun `Empty queue name fails`() {
        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.queueName.text = ""
            }

            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Invalid standard queue name fails`() {
        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.queueName.text = INVALID_STANDARD_NAME
            }

            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Invalid fifo queue name fails`() {
        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.fifoType.isSelected = true
                view.queueName.text = INVALID_FIFO_NAME
            }

            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Fifo queue name missing suffix fails`() {
        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.queueName.text = VALID_STANDARD_NAME
                view.fifoType.isSelected = true
            }

            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Standard queue created`() {
        val createQueueCaptor = argumentCaptor<CreateQueueRequest>()
        client.stub {
            on { createQueue(createQueueCaptor.capture()) } doReturn CreateQueueResponse.builder().build()
        }

        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.queueName.text = VALID_STANDARD_NAME
            }
            dialog.createQueue()
        }

        assertThat(createQueueCaptor.firstValue.queueName()).isEqualTo(VALID_STANDARD_NAME)
    }

    @Test
    fun `Fifo queue created`() {
        val createQueueCaptor = argumentCaptor<CreateQueueRequest>()
        client.stub {
            on { createQueue(createQueueCaptor.capture()) } doReturn CreateQueueResponse.builder().build()
        }

        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.queueName.text = VALID_FIFO_NAME
                view.fifoType.isSelected = true
            }
            dialog.createQueue()
        }

        assertThat(createQueueCaptor.firstValue.queueName()).isEqualTo(VALID_FIFO_NAME)
    }

    @Test
    fun `Error creating queue`() {
        val createQueueCaptor = argumentCaptor<CreateQueueRequest>()
        client.stub {
            on { createQueue(createQueueCaptor.capture()) } doThrow QueueNameExistsException.builder().message(ERROR_MESSAGE).build()
        }

        runInEdtAndWait {
            val dialog = CreateQueueDialog(projectRule.project, client).apply {
                view.queueName.text = VALID_STANDARD_NAME
            }
            assertThatThrownBy { dialog.createQueue() }.hasMessage(ERROR_MESSAGE)
        }

        assertThat(createQueueCaptor.firstValue.queueName()).isEqualTo(VALID_STANDARD_NAME)
    }

    private companion object {
        const val INVALID_STANDARD_NAME = "Hello_World!"
        const val INVALID_FIFO_NAME = "Hello_World!.fifo"
        const val VALID_STANDARD_NAME = "Hello-World"
        const val VALID_FIFO_NAME = "Hello-World.fifo"
        const val ERROR_MESSAGE = "Queue already exists."
    }
}
