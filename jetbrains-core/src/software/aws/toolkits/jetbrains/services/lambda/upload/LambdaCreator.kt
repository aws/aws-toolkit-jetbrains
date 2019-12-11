// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.io.size
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest
import software.amazon.awssdk.services.lambda.model.FunctionCode
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.services.lambda.LambdaBuilder
import software.aws.toolkits.jetbrains.services.lambda.LambdaBuilderUtils
import software.aws.toolkits.jetbrains.services.lambda.LambdaFunction
import software.aws.toolkits.jetbrains.services.lambda.PackageLambdaFromHandler
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import software.aws.toolkits.jetbrains.services.lambda.sam.SamOptions
import software.aws.toolkits.jetbrains.services.lambda.toDataClass
import software.aws.toolkits.jetbrains.utils.ProgressMonitorInputStream
import software.aws.toolkits.resources.message
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

object LambdaCreatorFactory {
    fun create(clientManager: ToolkitClientManager, builder: LambdaBuilder): LambdaCreator = LambdaCreator(
        builder,
        CodeUploader(clientManager.getClient()),
        LambdaFunctionCreator(clientManager.getClient())
    )
}

class LambdaCreator internal constructor(
    private val builder: LambdaBuilder,
    private val uploader: CodeUploader,
    private val functionCreator: LambdaFunctionCreator
) {
    fun createLambda(
        module: Module,
        handler: PsiElement,
        functionDetails: FunctionUploadDetails,
        s3Bucket: String
    ): CompletionStage<LambdaFunction> = packageLambda(handler, functionDetails, module, builder)
        .thenCompose { uploader.upload(functionDetails, it, s3Bucket, module.project) }
        .thenCompose { functionCreator.create(module.project, functionDetails, it) }

    fun updateLambda(
        module: Module,
        handler: PsiElement,
        functionDetails: FunctionUploadDetails,
        s3Bucket: String,
        replaceConfiguration: Boolean = true
    ): CompletionStage<Nothing> = packageLambda(handler, functionDetails, module, builder)
        .thenCompose { uploader.upload(functionDetails, it, s3Bucket, module.project) }
        .thenCompose { functionCreator.update(functionDetails, it, replaceConfiguration) }

    private fun packageLambda(
        handler: PsiElement,
        functionDetails: FunctionUploadDetails,
        module: Module,
        builder: LambdaBuilder
    ): CompletionStage<Path> {
        val request = PackageLambdaFromHandler(
            handler,
            functionDetails.handler,
            functionDetails.runtime,
            SamOptions()
        )

        // We should never hit this point since validation logic of the UI should validate this cant be null
        val runtimeGroup = functionDetails.runtime.runtimeGroup
            ?: throw IllegalArgumentException("RuntimeGroup not defined for ${functionDetails.runtime}")

        return LambdaBuilderUtils.packageAndReport(module, runtimeGroup, request, builder)
    }
}

class LambdaFunctionCreator(private val lambdaClient: LambdaClient) {
    fun create(
        project: Project,
        details: FunctionUploadDetails,
        uploadedCode: UploadedCode
    ): CompletionStage<LambdaFunction> {
        val future = CompletableFuture<LambdaFunction>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val code = FunctionCode.builder().s3Bucket(uploadedCode.bucket).s3Key(uploadedCode.key)
                uploadedCode.version?.run { code.s3ObjectVersion(this) }
                val req = CreateFunctionRequest.builder()
                    .handler(details.handler)
                    .functionName(details.name)
                    .role(details.iamRole.arn)
                    .runtime(details.runtime)
                    .description(details.description)
                    .timeout(details.timeout)
                    .memorySize(details.memorySize)
                    .code(code.build())
                    .environment {
                        it.variables(details.envVars)
                    }
                    .tracingConfig {
                        it.mode(details.tracingMode)
                    }
                    .build()

                val settingsManager = ProjectAccountSettingsManager.getInstance(project)
                val result = lambdaClient.createFunction(req)
                future.complete(
                    result.toDataClass(
                        settingsManager.activeCredentialProvider.id,
                        settingsManager.activeRegion
                    )
                )
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun update(details: FunctionUploadDetails, uploadedCode: UploadedCode, replaceConfiguration: Boolean): CompletionStage<Nothing> {
        val future = CompletableFuture<Nothing>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val req = UpdateFunctionCodeRequest.builder()
                    .functionName(details.name)
                    .s3Bucket(uploadedCode.bucket)
                    .s3Key(uploadedCode.key)

                uploadedCode.version?.let { version -> req.s3ObjectVersion(version) }

                lambdaClient.updateFunctionCode(req.build())
                if (replaceConfiguration) {
                    updateInternally(details)
                }
                future.complete(null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun update(details: FunctionUploadDetails): CompletionStage<Nothing> {
        val future = CompletableFuture<Nothing>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                updateInternally(details)
                future.complete(null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun updateInternally(details: FunctionUploadDetails) {
        val req = UpdateFunctionConfigurationRequest.builder()
            .handler(details.handler)
            .functionName(details.name)
            .role(details.iamRole.arn)
            .runtime(details.runtime)
            .description(details.description)
            .timeout(details.timeout)
            .memorySize(details.memorySize)
            .environment {
                it.variables(details.envVars)
            }
            .tracingConfig {
                it.mode(details.tracingMode)
            }
            .build()

        lambdaClient.updateFunctionConfiguration(req)
    }
}

class CodeUploader(private val s3Client: S3Client) {
    fun upload(
        functionDetails: FunctionUploadDetails,
        code: Path,
        s3Bucket: String,
        project: Project
    ): CompletionStage<UploadedCode> {
        val future = CompletableFuture<UploadedCode>()
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            message("lambda.create.uploading"),
            true,
            ALWAYS_BACKGROUND
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                try {
                    val key = "${functionDetails.name}.zip"
                    val por = PutObjectRequest.builder().bucket(s3Bucket).key(key).build()
                    ProgressMonitorInputStream.fromFile(indicator, code, noOpReset = true).use { inputStream ->
                        val result = s3Client.putObject(por, RequestBody.fromInputStream(inputStream, code.size()))
                        future.complete(UploadedCode(s3Bucket, key, result.versionId()))
                    }
                } catch (e: Exception) {
                    future.completeExceptionally(RuntimeException(message("lambda.create.failed_to_upload"), e))
                }
            }
        })
        return future
    }
}

data class UploadedCode(val bucket: String, val key: String, val version: String?)
