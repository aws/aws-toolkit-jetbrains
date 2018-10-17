// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.util.io.FileUtil
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse
import software.amazon.awssdk.services.lambda.model.Runtime
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.aws.toolkits.jetbrains.core.MockClientManager
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackage
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackager
import software.aws.toolkits.jetbrains.testutils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class LambdaCreatorTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    private lateinit var mockClientManager: MockClientManager

    @Before
    fun setUp() {
        mockClientManager = MockClientManager.getInstance(projectRule.project)
        mockClientManager.reset()
    }

    @Test
    fun testCreation() {
        val functionDetails = FunctionUploadDetails(
            name = "TestFunction",
            handler = "com.example.UsefulUtils::upperCase",
            iamRole = IamRole("TestRole", "TestRoleArn"),
            s3Bucket = "TestBucket",
            runtime = Runtime.JAVA8,
            description = "TestDescription",
            envVars = mapOf("TestKey" to "TestValue"),
            timeout = 60
        )

        val uploadCaptor = argumentCaptor<PutObjectRequest>()
        val s3Client = delegateMock<S3Client> {
            on { putObject(uploadCaptor.capture(), any<Path>()) } doReturn PutObjectResponse.builder()
                .versionId("VersionFoo")
                .build()
        }

        mockClientManager.register(S3Client::class, s3Client)

        val createCaptor = argumentCaptor<CreateFunctionRequest>()
        val lambdaClient = delegateMock<LambdaClient> {
            on { createFunction(createCaptor.capture()) } doReturn CreateFunctionResponse.builder()
                .functionName(functionDetails.name)
                .functionArn("TestFunctionArn")
                .description(functionDetails.description)
                .lastModified(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .handler(functionDetails.handler)
                .runtime(functionDetails.runtime)
                .build()
        }

        mockClientManager.register(LambdaClient::class, lambdaClient)

        val tempFile = FileUtil.createTempFile("lambda", ".zip")

        val packager = mock<LambdaPackager> {
            on { createPackage(any(), any()) } doReturn CompletableFuture.completedFuture(LambdaPackage(tempFile.toPath()))
        }

        val psiFile = projectRule.fixture.addClass(
            """
            package com.example;

            public class UsefulUtils {
                public static String upperCase(String input) {
                    return input.toUpperCase();
                }
            }
            """
        ).containingFile

        val lambdaCreator = LambdaCreatorFactory.create(mockClientManager, packager)
        lambdaCreator.createLambda(functionDetails, projectRule.module, psiFile).toCompletableFuture()
            .get(5, TimeUnit.SECONDS)

        val uploadRequest = uploadCaptor.firstValue
        assertThat(uploadRequest.bucket()).isEqualTo(functionDetails.s3Bucket)
        assertThat(uploadRequest.key()).isEqualTo("${functionDetails.name}.zip")

        val createRequest = createCaptor.firstValue
        assertThat(createRequest.functionName()).isEqualTo(functionDetails.name)
        assertThat(createRequest.description()).isEqualTo(functionDetails.description)
        assertThat(createRequest.handler()).isEqualTo(functionDetails.handler)
        assertThat(createRequest.environment().variables()).isEqualTo(functionDetails.envVars)
        assertThat(createRequest.role()).isEqualTo(functionDetails.iamRole.arn)
        assertThat(createRequest.runtime()).isEqualTo(functionDetails.runtime)
        assertThat(createRequest.timeout()).isEqualTo(functionDetails.timeout)
        assertThat(createRequest.code().s3Bucket()).isEqualTo(functionDetails.s3Bucket)
        assertThat(createRequest.code().s3Key()).isEqualTo("${functionDetails.name}.zip")
        assertThat(createRequest.code().s3ObjectVersion()).isEqualTo("VersionFoo")
    }
}