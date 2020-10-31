// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload.steps

import software.amazon.awssdk.services.lambda.LambdaClient
import software.aws.toolkits.jetbrains.services.lambda.upload.FunctionUploadDetails
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.PackageLambda.Companion.UPLOADED_CODE_LOCATION
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.MessageEmitter
import software.aws.toolkits.jetbrains.utils.execution.steps.Step

class UpdateLambda(private val lambdaClient: LambdaClient, private val functionName: String, private val updatedDetails: FunctionUploadDetails?) : Step() {
    override val stepName = "Updating Lambda"

    override fun execute(context: Context, messageEmitter: MessageEmitter, ignoreCancellation: Boolean) {
        val codeLocation = context.getRequiredAttribute(UPLOADED_CODE_LOCATION)
        lambdaClient.updateFunctionCode {
            it.functionName(functionName)
            it.s3Bucket(codeLocation.bucket)
            it.s3Key(codeLocation.key)
            it.s3ObjectVersion(codeLocation.version)
        }

        updatedDetails?.let {
            lambdaClient.updateFunctionConfiguration {
                it.functionName(updatedDetails.name)
                it.description(updatedDetails.description)
                it.handler(updatedDetails.handler)
                it.role(updatedDetails.iamRole.arn)
                it.runtime(updatedDetails.runtime)
                it.timeout(updatedDetails.timeout)
                it.memorySize(updatedDetails.memorySize)
                it.environment { env ->
                    env.variables(updatedDetails.envVars)
                }
                it.tracingConfig { tracing ->
                    tracing.mode(updatedDetails.tracingMode)
                }
            }
        }
    }
}
