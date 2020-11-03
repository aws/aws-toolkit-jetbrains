// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload.steps

import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.Runtime
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.lambda.upload.FunctionDetails

class UpdateLambdaConfigurationTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val clientManagerRule = MockClientManagerRule()

    @Test
    fun `can update the configuration`() {
        validate(
            FunctionDetails(
                name = aString(),
                handler = aString(),
                iamRole = IamRole(aString()),
                runtime = Runtime.knownValues().random(),
                description = aString(),
                envVars = mapOf(aString() to aString()),
                timeout = 300,
                memorySize = 1024,
                xrayEnabled = true
            )
        )
    }

    private fun validate(functionDetails: FunctionDetails) {
        val configRequestCaptor = argumentCaptor<UpdateFunctionConfigurationRequest>()
        val lambdaClient = clientManagerRule.create<LambdaClient>().stub {
            on { updateFunctionConfiguration(configRequestCaptor.capture()) } doReturn UpdateFunctionConfigurationResponse.builder().build()
        }

        lambdaClient.updateFunctionConfiguration(functionDetails)

        verify(lambdaClient).updateFunctionConfiguration(any<UpdateFunctionConfigurationRequest>())
        with(configRequestCaptor) {
            assertThat(allValues).hasSize(1)

            assertThat(firstValue.functionName()).isEqualTo(functionDetails.name)
            assertThat(firstValue.description()).isEqualTo(functionDetails.description)
            assertThat(firstValue.environment().variables()).isEqualTo(functionDetails.envVars)
            assertThat(firstValue.handler()).isEqualTo(functionDetails.handler)
            assertThat(firstValue.runtime()).isEqualTo(functionDetails.runtime)
            assertThat(firstValue.role()).isEqualTo(functionDetails.iamRole.arn)
            assertThat(firstValue.memorySize()).isEqualTo(functionDetails.memorySize)
            assertThat(firstValue.timeout()).isEqualTo(functionDetails.timeout)
            assertThat(firstValue.tracingConfig().mode()).isEqualTo(functionDetails.tracingMode)
        }
    }
}
