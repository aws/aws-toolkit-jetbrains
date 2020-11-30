// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.deploy

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.services.lambda.sam.SamOptions
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.BuildLambda
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.DeployLambda
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.PackageLambda
import software.aws.toolkits.jetbrains.utils.execution.steps.StepWorkflow
import java.nio.file.Files
import java.nio.file.Paths

fun createDeployWorkflow(
    project: Project,
    stackName: String,
    template: VirtualFile,
    s3Bucket: String,
    useContainer: Boolean,
    parameters: Map<String, String>,
    capabilities: List<CreateCapabilities>
): StepWorkflow {
    val credentialsProvider = AwsConnectionManager.getInstance(project).activeCredentialProvider
    val region = AwsConnectionManager.getInstance(project).activeRegion
    val buildDir = Paths.get(template.parent.path, SamCommon.SAM_BUILD_DIR, "build")
    val builtTemplate = buildDir.resolve("template.yaml")
    val packagedTemplate = builtTemplate.parent.resolve("packaged-${builtTemplate.fileName}")
    val templatePath = Paths.get(template.path)

    Files.createDirectories(buildDir)

    return StepWorkflow(
        BuildLambda(templatePath, buildDir, createCommonEnvVars(credentialsProvider, region), SamOptions(buildInContainer = useContainer)),
        PackageLambda(builtTemplate, packagedTemplate, null, s3Bucket, createCommonEnvVars(credentialsProvider, region)),
        DeployLambda(packagedTemplate, stackName, s3Bucket, capabilities, parameters, createCommonEnvVars(credentialsProvider, region), region)
    )
}

private fun createCommonEnvVars(credentialsProvider: ToolkitCredentialsProvider, region: AwsRegion): Map<String, String> {
    val envVars = mutableMapOf<String, String>()

    envVars.putAll(region.toEnvironmentVariables())
    envVars.putAll(credentialsProvider.resolveCredentials().toEnvironmentVariables())

    return envVars
}
