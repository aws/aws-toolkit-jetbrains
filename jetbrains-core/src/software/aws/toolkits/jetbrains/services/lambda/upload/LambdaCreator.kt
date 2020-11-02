// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.services.lambda.sam.SamOptions
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateUtils
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.BuildLambda
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.CreateLambda
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.PackageLambda
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.UpdateLambdaCode
import software.aws.toolkits.jetbrains.utils.execution.steps.StepWorkflow
import java.nio.file.Files
import java.nio.file.Path

fun createLambdaWorkflow(
    project: Project,
    functionUploadDetails: FunctionUploadDetails,
    buildDir: Path,
    codeUri: Path,
    codeStorageLocation: String,
    samOptions: SamOptions,
    functionDetails: FunctionUploadDetails
): StepWorkflow {
    Files.createDirectories(buildDir)

    val dummyTemplate = Files.createTempFile("temp-template", ".yaml")
    val dummyLogicalId = "Function"
    SamTemplateUtils.writeDummySamTemplate(
        tempFile = dummyTemplate,
        logicalId = dummyLogicalId,
        runtime = functionUploadDetails.runtime,
        handler = functionUploadDetails.handler,
        codeUri = codeUri.toString()
    )
    val packagedTemplate = buildDir.resolve("packaged-temp-template.yaml")

    val connectSettings = AwsConnectionManager.getInstance(project).connectionSettings()
        ?: throw IllegalStateException("Tried to update a lambda without valid AWS connection")

    val envVars = connectSettings.credentials.resolveCredentials().toEnvironmentVariables() + connectSettings.region.toEnvironmentVariables()

    return StepWorkflow(
        BuildLambda(dummyTemplate, buildDir, samOptions),
        PackageLambda(dummyTemplate, packagedTemplate, dummyLogicalId, codeStorageLocation, envVars),
        CreateLambda(project.awsClient(), functionDetails)
    )
}

fun updateLambdaCodeWorkflow(
    project: Project,
    functionUploadDetails: FunctionUploadDetails,
    buildDir: Path,
    codeUri: Path,
    codeStorageLocation: String,
    samOptions: SamOptions,
    updatedFunctionDetails: FunctionUploadDetails?
): StepWorkflow {
    Files.createDirectories(buildDir)

    val dummyTemplate = Files.createTempFile("temp-template", ".yaml")
    val dummyLogicalId = "Function"
    SamTemplateUtils.writeDummySamTemplate(
        tempFile = dummyTemplate,
        logicalId = dummyLogicalId,
        runtime = functionUploadDetails.runtime,
        handler = functionUploadDetails.handler,
        codeUri = codeUri.toString()
    )
    val packagedTemplate = buildDir.resolve("packaged-temp-template.yaml")

    val connectSettings = AwsConnectionManager.getInstance(project).connectionSettings()
        ?: throw IllegalStateException("Tried to update a lambda without valid AWS connection")

    val envVars = connectSettings.credentials.resolveCredentials().toEnvironmentVariables() + connectSettings.region.toEnvironmentVariables()

    return StepWorkflow(
        BuildLambda(dummyTemplate, buildDir, samOptions),
        PackageLambda(dummyTemplate, packagedTemplate, dummyLogicalId, codeStorageLocation, envVars),
        UpdateLambdaCode(project.awsClient(), functionUploadDetails.name, updatedFunctionDetails)
    )
}
