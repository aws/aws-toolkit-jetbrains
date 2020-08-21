// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.awscore.client.builder.AwsDefaultClientBuilder
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.core.signer.Signer
import software.amazon.awssdk.http.SdkHttpClient
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.region.Endpoint
import software.aws.toolkits.core.region.Service
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.MockAwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.credentials.waitUntilConnectionStateIsStable
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class AwsClientManagerTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val temporaryDirectory = TemporaryFolder()

    private lateinit var mockCredentialManager: MockCredentialsManager

    @Before
    fun setUp() {
        mockCredentialManager = MockCredentialsManager.getInstance()
        mockCredentialManager.reset()
        MockRegionProvider.getInstance().reset()
        MockAwsConnectionManager.getInstance(projectRule.project).reset()
    }

    @After
    fun tearDown() {
        MockAwsConnectionManager.getInstance(projectRule.project).reset()
        MockRegionProvider.getInstance().reset()
        mockCredentialManager.reset()
    }

    @Test
    fun canGetAnInstanceOfAClient() {
        val sut = getClientManager()
        val client = sut.getClient<DummyServiceClient>()
        assertThat(client.serviceName()).isEqualTo("dummyClient")
    }

    @Test
    fun clientsAreCached() {
        val sut = getClientManager()
        val fooClient = sut.getClient<DummyServiceClient>()
        val barClient = sut.getClient<DummyServiceClient>()

        assertThat(fooClient).isSameAs(barClient)
    }

    @Test
    fun oldClientsAreRemovedWhenProfilesAreRemoved() {
        val sut = getClientManager()

        val credentialsIdentifier = mockCredentialManager.addCredentials("profile:admin")
        val credentialProvider = mockCredentialManager.getAwsCredentialProvider(credentialsIdentifier, MockRegionProvider.getInstance().defaultRegion())

        sut.getClient<DummyServiceClient>(credentialProvider)

        assertThat(sut.cachedClients().keys).anySatisfy {
            it.credentialProviderId == "profile:admin"
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(CredentialManager.CREDENTIALS_CHANGED).providerRemoved(credentialsIdentifier)

        assertThat(sut.cachedClients().keys).noneSatisfy {
            it.credentialProviderId == "profile:admin"
        }
    }

    @Test
    fun clientsAreClosedWhenProjectIsDisposed() {
        val sut = getClientManager(projectRule.project)
        val client = sut.getClient<DummyServiceClient>()

        // Frameworks handle this normally but we can't trigger it from tests
        Disposer.dispose(sut)

        assertThat(client.closed).isTrue()
    }

    @Test
    fun httpClientIsSharedAcrossClients() {
        val sut = getClientManager()
        val dummy = sut.getClient<DummyServiceClient>()
        val secondDummy = sut.getClient<SecondDummyServiceClient>()

        assertThat(dummy.httpClient.delegate).isSameAs(secondDummy.httpClient.delegate)
    }

    @Test
    fun clientWithoutBuilderFailsDescriptively() {
        val sut = getClientManager()

        assertThatThrownBy { sut.getClient<InvalidServiceClient>() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("builder()")
    }

    @Test
    fun clientInterfaceWithoutNameFieldFailsDescriptively() {
        val sut = getClientManager()

        assertThatThrownBy { sut.getClient<NoServiceNameClient>() }
            .isInstanceOf(NoSuchFieldException::class.java)
            .hasMessageContaining("SERVICE_NAME")
    }

    @Test
    fun newClientCreatedWhenRegionChanges() {
        val sut = getClientManager()
        val first = sut.getClient<DummyServiceClient>()

        val testSettings = MockAwsConnectionManager.getInstance(projectRule.project)
        testSettings.changeRegionAndWait(AwsRegion("us-west-2", "us-west-2", "aws"))

        testSettings.waitUntilConnectionStateIsStable()

        val afterRegionUpdate = sut.getClient<DummyServiceClient>()

        assertThat(afterRegionUpdate).isNotSameAs(first)
    }

    @Test
    fun globalServicesCanBeGivenAnyRegion() {
        val sut = getClientManager()
        MockRegionProvider.getInstance().addService("DummyService", Service(
            endpoints = mapOf("global" to Endpoint()),
            isRegionalized = false,
            partitionEndpoint = "global"
        ))
        val first = sut.getClient<DummyServiceClient>(regionOverride = AwsRegion("us-east-1", "us-east-1", "aws"))
        val second = sut.getClient<DummyServiceClient>(regionOverride = AwsRegion("us-west-2", "us-west-2", "aws"))

        assertThat(first.serviceName()).isEqualTo("dummyClient")
        assertThat(second).isSameAs(first)
    }

    // Test against real version so bypass ServiceManager for the client manager
    private fun getClientManager(project: Project = projectRule.project) = AwsClientManager(project)

    class DummyServiceClient(val httpClient: SdkHttpClient) : TestClient() {
        companion object {
            @Suppress("unused")
            @JvmStatic
            fun builder() = DummyServiceClientBuilder()
        }
    }

    class DummyServiceClientBuilder : TestClientBuilder<DummyServiceClientBuilder, DummyServiceClient>() {
        override fun serviceName(): String = "DummyService"

        override fun signingName(): String = serviceName()

        override fun buildClient() = DummyServiceClient(syncClientConfiguration().option(SdkClientOption.SYNC_HTTP_CLIENT))
    }

    class SecondDummyServiceClient(val httpClient: SdkHttpClient) : TestClient() {
        companion object {
            @Suppress("unused")
            @JvmStatic
            fun builder() = SecondDummyServiceClientBuilder()
        }
    }

    class SecondDummyServiceClientBuilder :
        TestClientBuilder<SecondDummyServiceClientBuilder, SecondDummyServiceClient>() {
        override fun serviceName(): String = "SecondDummyService"

        override fun signingName(): String = serviceName()

        override fun buildClient() = SecondDummyServiceClient(syncClientConfiguration().option(SdkClientOption.SYNC_HTTP_CLIENT))
    }

    class InvalidServiceClient : TestClient() {
        override fun close() {}

        override fun serviceName() = "invalidClient"
    }

    class NoServiceNameClient : SdkClient {
        override fun close() {}

        override fun serviceName() = "invalidClient"
    }

    abstract class TestClient : SdkClient, AutoCloseable {
        var closed = false

        override fun serviceName() = "dummyClient"

        override fun close() {
            closed = true
        }

        companion object {
            @Suppress("unused")
            @JvmField
            val SERVICE_NAME = "DummyService"
        }
    }

    abstract class TestClientBuilder<B : AwsClientBuilder<B, C>, C> : AwsDefaultClientBuilder<B, C>() {
        init {
            overrideConfiguration {
                it.advancedOptions(mapOf(SdkAdvancedClientOption.SIGNER to Signer { _, _ -> throw NotImplementedError() }))
            }
        }

        override fun serviceEndpointPrefix() = "dummyClient"
    }

    private val SdkHttpClient.delegate: SdkHttpClient
        get() {
            val delegateProperty = this::class.declaredMemberProperties.find { it.name == "delegate" }
                ?: throw IllegalArgumentException(
                    "Expected instance of software.amazon.awssdk.core.client.builder.SdkDefaultClientBuilder.NonManagedSdkHttpClient"
                )
            delegateProperty.isAccessible = true
            return delegateProperty.call(this) as SdkHttpClient
        }
}
