// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.testFramework.ProjectRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.aws.toolkit.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkit.core.region.AwsRegion
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import java.util.concurrent.CompletableFuture

class CfnCredentialsServiceTest {

    @get:Rule
    val projectRule = ProjectRule()

    private lateinit var mockConnectionManager: AwsConnectionManager
    private lateinit var mockCredentialProvider: ToolkitCredentialsProvider
    private lateinit var mockCfnClient: CfnClientService
    private lateinit var credentialsService: CfnCredentialsService

    @Before
    fun setUp() {
        mockConnectionManager = mockk()
        mockCredentialProvider = mockk()
        mockCfnClient = mockk()

        mockkObject(AwsConnectionManager)
        mockkObject(CfnClientService)

        every { AwsConnectionManager.getInstance(projectRule.project) } returns mockConnectionManager
        every { CfnClientService.getInstance(projectRule.project) } returns mockCfnClient
        every { mockCfnClient.updateIamCredentials(any()) } returns CompletableFuture.completedFuture(mockk())

        // Mock the selectedRegion call that happens in constructor
        every { mockConnectionManager.selectedRegion } returns null

        credentialsService = CfnCredentialsService(projectRule.project)
    }

    @After
    fun tearDown() {
        unmockkObject(AwsConnectionManager)
        unmockkObject(CfnClientService)
    }

    @Test
    fun `sendCredentials excludes null sessionToken from payload`() {
        val testRegion = AwsRegion("us-east-1", "US East 1", "aws")
        val basicCredentials = AwsBasicCredentials.create("testAccessKey", "testSecretKey")
        var capturedParams: UpdateCredentialsParams? = null

        every { mockConnectionManager.selectedRegion } returns testRegion
        every { mockConnectionManager.activeCredentialProvider } returns mockCredentialProvider
        every { mockCredentialProvider.shortName } returns "testProfile"
        every { mockCredentialProvider.resolveCredentials() } returns basicCredentials
        every { mockCfnClient.updateIamCredentials(capture(slot<UpdateCredentialsParams>())) } answers {
            capturedParams = firstArg()
            CompletableFuture.completedFuture(mockk())
        }

        credentialsService.sendCredentials()

        verify { mockCfnClient.updateIamCredentials(any()) }

        // Verify the encrypted payload is sent
        assertThat(capturedParams).isNotNull()
        capturedParams?.let {
            assertThat(it.encrypted).isTrue()
            assertThat(it.data).isNotEmpty()
        }

        // Use reflection to verify the mapper excludes null sessionToken
        val mapperField = CfnCredentialsService::class.java.getDeclaredField("MAPPER")
        mapperField.isAccessible = true
        val mapper = mapperField.get(null) as com.fasterxml.jackson.databind.ObjectMapper

        val testCredentials = IamCredentials("testProfile", "us-east-1", "testAccessKey", "testSecretKey", null)
        val json = mapper.writeValueAsString(testCredentials)

        assertThat(json).doesNotContain("sessionToken")
    }

    @Test
    fun `sendCredentials includes sessionToken when present`() {
        val testRegion = AwsRegion("us-east-1", "US East 1", "aws")
        val sessionCredentials = AwsSessionCredentials.create("testAccessKey", "testSecretKey", "testSessionToken")
        var capturedParams: UpdateCredentialsParams? = null

        every { mockConnectionManager.selectedRegion } returns testRegion
        every { mockConnectionManager.activeCredentialProvider } returns mockCredentialProvider
        every { mockCredentialProvider.shortName } returns "testProfile"
        every { mockCredentialProvider.resolveCredentials() } returns sessionCredentials
        every { mockCfnClient.updateIamCredentials(capture(slot<UpdateCredentialsParams>())) } answers {
            capturedParams = firstArg()
            CompletableFuture.completedFuture(mockk())
        }

        credentialsService.sendCredentials()

        verify { mockCfnClient.updateIamCredentials(any()) }

        // Verify the encrypted payload is sent
        assertThat(capturedParams).isNotNull()
        capturedParams?.let {
            assertThat(it.encrypted).isTrue()
            assertThat(it.data).isNotEmpty()
        }

        // Use reflection to verify the mapper includes non-null sessionToken
        val mapperField = CfnCredentialsService::class.java.getDeclaredField("MAPPER")
        mapperField.isAccessible = true
        val mapper = mapperField.get(null) as com.fasterxml.jackson.databind.ObjectMapper

        val testCredentials = IamCredentials("testProfile", "us-east-1", "testAccessKey", "testSecretKey", "testSessionToken")
        val json = mapper.writeValueAsString(testCredentials)

        assertThat(json).contains("\"sessionToken\":\"testSessionToken\"")
    }

    @Test
    fun `sendCredentials returns early when no region selected`() {
        every { mockConnectionManager.selectedRegion } returns null

        credentialsService.sendCredentials()

        verify(exactly = 0) { mockCfnClient.updateIamCredentials(any()) }
    }
}
