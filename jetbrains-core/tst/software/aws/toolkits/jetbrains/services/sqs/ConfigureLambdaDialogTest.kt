// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest
import software.amazon.awssdk.services.lambda.model.InvalidParameterValueException
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider

class ConfigureLambdaDialogTest {
    lateinit var sqsClient: SqsClient
    lateinit var lambdaClient: LambdaClient
    lateinit var region: AwsRegion
    lateinit var queue: Queue

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    @Before
    fun setup() {
        sqsClient = mockClientManagerRule.create()
        lambdaClient = mockClientManagerRule.create()
        region = MockRegionProvider.getInstance().defaultRegion()
        queue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/test", region)
    }

    @Test
    fun `No function selected`() {
        runInEdtAndWait {
            val dialog = ConfigureLambdaDialog(projectRule.project, sqsClient, queue)
            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `No function arn specified`() {
        runInEdtAndWait {
            val dialog = ConfigureLambdaDialog(projectRule.project, sqsClient, queue).apply {
                view.inputButton.isSelected = true
                view.functionArn.text = ""
            }
            val validationInfo = dialog.validate()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Specified function does not have permission`() {
        whenever(lambdaClient.createEventSourceMapping(any<CreateEventSourceMappingRequest>())).then {
            InvalidParameterValueException.builder().build()
        }

        runInEdtAndWait {
            val dialog = ConfigureLambdaDialog(projectRule.project, sqsClient, queue).apply {
                view.inputButton.isSelected = true
                view.functionArn.text = TEST_FUNCTION_NAME
            }
            // TODO: Finish test
        }
    }

    private companion object {
        const val TEST_FUNCTION_NAME = "Function"
    }
}
